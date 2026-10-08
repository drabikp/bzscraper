package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Platforms;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.StepType;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncTask;
import sk.drabikp.bzscraper.domain.service.MootWork;
import sk.drabikp.bzscraper.domain.service.SyncRetryPolicy;
import sk.drabikp.bzscraper.domain.service.Workflows;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs sync tasks as workflows ({@link Workflows}): each task is a run that goes through its
 * action's steps in order, using the steps its platform provides ({@link StepRegistry}) — see
 * docs/sync-workflow-plan.md.
 * <ul>
 *   <li>The due runs at one step on one platform go together: a batch step gets up to its
 *       batch size in one call; a one-at-a-time step up to {@value #SINGLE_PER_PASS} gigs,
 *       each result saved before the next, then other work gets its turn.</li>
 *   <li>Before a run: work that became moot ends done ({@link MootWork}); a step that no
 *       longer takes the gig passes it on.</li>
 *   <li>Outcomes: done (with the platform record change: a created event's id recorded, a
 *       removed one's forgotten — in the same transaction); refused (nothing happened there)
 *       → the next step; failed → this step again later, as {@link SyncRetryPolicy} allows
 *       (never for creates: one may have happened anyway); failed for good → the user;
 *       postponed (the platform's browser was busy) → this step again in a minute.</li>
 *   <li>Each batch counts for or against its platform's health; paused or held back, no
 *       further batch starts.</li>
 * </ul>
 */
public class WorkflowEngine {

    static final int SINGLE_PER_PASS = 10;

    private final StepRegistry steps;
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final SyncOutbox outbox;
    private final Transactions transactions;
    private final SyncNotifier notifier;
    private final Clock clock;
    private final Platforms platforms;
    private final PauseSyncUseCase pause;
    private final PlatformHealth health;

    public WorkflowEngine(StepRegistry steps, GigRepository gigRepository, PublishedGigStore publishedGigStore,
                          SyncOutbox outbox, Transactions transactions, SyncNotifier notifier, Clock clock,
                          Platforms platforms, PauseSyncUseCase pause, PlatformHealth health) {
        this.steps = steps;
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.outbox = outbox;
        this.transactions = transactions;
        this.notifier = notifier;
        this.clock = clock;
        this.platforms = platforms;
        this.pause = pause;
        this.health = health;
    }

    /** Runs the work due at {@code task}'s step on its platform — {@code task} and the runs waiting with it. */
    public void runFrom(SyncTask task) {
        Platform platform = task.platform();
        StepType stepType = StepRegistry.stepOf(task);
        SyncStep step = steps.step(platform, stepType);
        List<SyncTask> waiting = outbox.due(platform, task.action(), clock.instant()).stream()
                .filter(t -> StepRegistry.stepOf(t) == stepType).toList();
        int limit = step == null ? waiting.size() : step.batchSize() == 1 ? SINGLE_PER_PASS : step.batchSize();

        List<SyncTask> runs = new ArrayList<>();
        List<SyncStep.Item> items = new ArrayList<>();
        for (SyncTask run : waiting.subList(0, Math.min(limit, waiting.size()))) {
            Optional<SyncStep.Item> item = prepare(run, stepType);
            if (item.isPresent()) {
                runs.add(run);
                items.add(item.get());
            }
        }
        for (int from = 0; from < runs.size() && !pause.paused() && !health.held(platform); from += step.batchSize()) {
            int to = Math.min(runs.size(), from + step.batchSize());
            runBatch(step, runs.subList(from, to), items.subList(from, to));
        }
        notifier.changed();
    }

    /**
     * Checks a run before its step: whether its work is still needed (the gig as it is now),
     * and whether this step still takes it — otherwise it moves on or ends here.
     */
    private Optional<SyncStep.Item> prepare(SyncTask run, StepType stepType) {
        Instant now = clock.instant();
        Platform platform = run.platform();
        Gig gig = gigRepository.findById(run.gigId()).orElse(null);
        String ref = publishedGigStore.externalRef(platform, run.gigId()).orElse(null);
        Optional<String> moot = MootWork.reason(run.action(), gig,
                publishedGigStore.isPublished(platform, run.gigId()), platforms.name(platform));
        if (moot.isPresent()) {
            outbox.markDone(run.id(), moot.get(), now);
            return Optional.empty();
        }
        if (run.action() != SyncAction.PUBLISH && ref == null) {
            outbox.markFailed(run.id(), "no " + platforms.name(platform) + " id was recorded — do it there by hand", now);
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        Optional<StepType> where = steps.firstTaking(platform, stepType, gig, reasons);
        if (where.isEmpty()) {
            outbox.markFailed(run.id(), steps.byHand(platform, reasons), now);
            return Optional.empty();
        }
        if (where.get() != stepType) {
            outbox.advance(run.id(), where.get(), String.join("; ", reasons), now);
            return Optional.empty();
        }
        return Optional.of(new SyncStep.Item(ref, gig));
    }

    private void runBatch(SyncStep step, List<SyncTask> runs, List<SyncStep.Item> items) {
        List<SyncTask> started = new ArrayList<>();
        List<SyncStep.Item> startedItems = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            if (outbox.markRunning(runs.get(i).id(), clock.instant())) {    // still PENDING (not discarded)
                started.add(runs.get(i));
                startedItems.add(items.get(i));
            }
        }
        if (started.isEmpty()) {
            return;
        }
        notifier.changed();
        List<StepOutcome> outcomes;
        try {
            outcomes = step.run(startedItems);
        } catch (RuntimeException e) {
            outcomes = started.stream().map(r -> StepOutcome.failed(String.valueOf(e.getMessage()))).toList();
        }
        List<StepOutcome> settled = new ArrayList<>();
        for (int i = 0; i < started.size(); i++) {
            StepOutcome outcome = i < outcomes.size() ? outcomes.get(i)
                    : StepOutcome.failed(step.type().label() + " gave no result for this gig");
            settle(started.get(i), step, startedItems.get(i).gig(), outcome);
            settled.add(outcome);
        }
        // the platform answered if anything but a temporary failure came back; postponed work
        // never reached it
        List<StepOutcome> reached = settled.stream().filter(o -> o.kind() != StepOutcome.Kind.POSTPONED).toList();
        if (!reached.isEmpty() && reached.stream().allMatch(o -> o.kind() == StepOutcome.Kind.FAILED)) {
            health.failed(step.platform(), step.type().label() + ": " + reached.getFirst().note());
        } else if (!reached.isEmpty()) {
            health.succeeded(step.platform());
        }
        notifier.changed();
    }

    private void settle(SyncTask run, SyncStep step, Gig gig, StepOutcome outcome) {
        Instant now = clock.instant();
        String label = step.type().label();
        switch (outcome.kind()) {
            case DONE -> transactions.inTransaction(() -> {
                // the gig's identity may have moved while the step ran: record under the current one
                GigId gigId = outbox.find(run.id()).map(SyncTask::gigId).orElse(run.gigId());
                if (outcome.ref() != null) {
                    publishedGigStore.record(run.platform(), gigId, outcome.ref());
                }
                if (run.action() == SyncAction.DELETE) {
                    publishedGigStore.remove(run.platform(), gigId);
                }
                outbox.markDone(run.id(),
                        label + (outcome.note() == null || outcome.note().isBlank() ? "" : ": " + outcome.note()), now);
            });
            case FAILED_FOR_GOOD -> outbox.markFailed(run.id(), label + ": " + outcome.note(), now);
            case POSTPONED -> outbox.postpone(run.id(), label + ": " + outcome.note(),
                    now.plus(SyncRetryPolicy.POSTPONE), now);
            case FAILED -> {
                Optional<Instant> retryAt = SyncRetryPolicy.nextAttempt(run.action(), run.attempts() + 1, now);
                if (retryAt.isPresent()) {
                    outbox.markRetry(run.id(), label + ": " + outcome.note(), retryAt.get(), now);
                } else {
                    outbox.markFailed(run.id(), label + ": " + outcome.note()
                            + (run.action().repeatable() ? "" : " — check " + platforms.name(run.platform())
                            + ", then Retry or Discard"), now);
                }
            }
            case REFUSED -> {
                // the platform said no and nothing happened there: another way may do it
                List<String> reasons = new ArrayList<>(List.of(label + ": " + outcome.note()));
                Optional<StepType> next = Workflows.after(step.type())
                        .flatMap(after -> steps.firstTaking(run.platform(), after, gig, reasons));
                if (next.isPresent()) {
                    outbox.advance(run.id(), next.get(), String.join("; ", reasons), now);
                } else {
                    outbox.markFailed(run.id(), steps.byHand(run.platform(), reasons), now);
                }
            }
        }
    }
}
