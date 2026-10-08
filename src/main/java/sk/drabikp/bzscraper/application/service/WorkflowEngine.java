package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.StepType;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncTask;
import sk.drabikp.bzscraper.domain.service.SyncRetryPolicy;
import sk.drabikp.bzscraper.domain.service.Workflows;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs sync tasks as workflows ({@link Workflows}): each task is a run that goes through
 * its action's steps in order, using the step implementations its platform has
 * ({@link SyncStep}) — see docs/sync-workflow-plan.md.
 * <ul>
 *   <li>A step that doesn't take the gig ({@link SyncStep#refusal}) or that the platform
 *       lacks is passed over; a run no step can do waits for the user ("by hand").</li>
 *   <li>The due runs at one step on one platform go together: a batch step gets up to its
 *       batch size in one call; a one-at-a-time step up to {@value #SINGLE_PER_PASS} gigs,
 *       each result saved before the next, then other work gets its turn.</li>
 *   <li>Outcomes: done; refused → the next step, if the action is safe to repeat; failed →
 *       this step again later ({@link SyncRetryPolicy}); failed for good → the user.</li>
 *   <li>The path is logged: "bulk edit: past event → form edit", "done — form edit".</li>
 * </ul>
 * Also answers, when work is queued, whether any step can do it ({@link #leftOut}).
 */
public class WorkflowEngine implements SyncAdmission {

    static final int SINGLE_PER_PASS = 10;

    private final Map<Platform, Map<StepType, SyncStep>> steps = new EnumMap<>(Platform.class);
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final SyncOutbox outbox;
    private final Transactions transactions;
    private final SyncNotifier notifier;
    private final Clock clock;

    public WorkflowEngine(List<SyncStep> steps, GigRepository gigRepository, PublishedGigStore publishedGigStore,
                          SyncOutbox outbox, Transactions transactions, SyncNotifier notifier, Clock clock) {
        for (SyncStep step : steps) {
            if (this.steps.computeIfAbsent(step.platform(), p -> new EnumMap<>(StepType.class))
                    .put(step.type(), step) != null) {
                throw new IllegalStateException("Two " + step.type() + " steps registered for " + step.platform());
            }
        }
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.outbox = outbox;
        this.transactions = transactions;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Whether the action runs as a workflow (the others still run the old way). */
    public boolean handles(SyncAction action) {
        return Workflows.of(action).isPresent();
    }

    @Override
    public Optional<String> leftOut(Platform platform, SyncAction action, Gig gig) {
        Optional<List<StepType>> workflow = Workflows.of(action);
        if (workflow.isEmpty()) {
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        return firstTaking(platform, workflow.get().getFirst(), gig, reasons).isPresent()
                ? Optional.empty() : Optional.of(byHand(platform, reasons));
    }

    /** Runs the work due at {@code task}'s step on its platform — {@code task} and the runs waiting with it. */
    public void runFrom(SyncTask task) {
        Platform platform = task.platform();
        StepType stepType = stepOf(task);
        SyncStep step = step(platform, stepType);
        Instant now = clock.instant();
        List<SyncTask> waiting = outbox.due(platform, task.action(), now).stream()
                .filter(t -> stepOf(t) == stepType).toList();
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
        for (int from = 0; from < runs.size(); from += step.batchSize()) {
            int to = Math.min(runs.size(), from + step.batchSize());
            runBatch(step, runs.subList(from, to), items.subList(from, to));
        }
        notifier.changed();
    }

    /**
     * Checks a run before its step: the gig and its platform copy still exist, and this step
     * still takes it (the gig may have changed) — otherwise it moves on or ends here.
     */
    private Optional<SyncStep.Item> prepare(SyncTask run, StepType stepType) {
        Instant now = clock.instant();
        Optional<Gig> gig = gigRepository.findById(run.gigId());
        if (gig.isEmpty()) {
            outbox.markDone(run.id(), "the gig is no longer in the catalog", now);
            return Optional.empty();
        }
        if (!publishedGigStore.isPublished(run.platform(), run.gigId())) {
            outbox.markDone(run.id(), "not on " + SyncRequests.platformName(run.platform()) + " — nothing to do", now);
            return Optional.empty();
        }
        Optional<String> ref = publishedGigStore.externalRef(run.platform(), run.gigId());
        if (ref.isEmpty()) {
            outbox.markFailed(run.id(), "no " + SyncRequests.platformName(run.platform())
                    + " id was recorded — do it there by hand", now);
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        Optional<StepType> where = firstTaking(run.platform(), stepType, gig.get(), reasons);
        if (where.isEmpty()) {
            outbox.markFailed(run.id(), byHand(run.platform(), reasons), now);
            return Optional.empty();
        }
        if (where.get() != stepType) {
            outbox.advance(run.id(), where.get(), String.join("; ", reasons), now);
            return Optional.empty();
        }
        return Optional.of(new SyncStep.Item(ref.get(), gig.get()));
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
        for (int i = 0; i < started.size(); i++) {
            StepOutcome outcome = i < outcomes.size() ? outcomes.get(i)
                    : StepOutcome.failed(step.type().label() + " gave no result for this gig");
            settle(started.get(i), step, startedItems.get(i).gig(), outcome);
        }
        notifier.changed();
    }

    private void settle(SyncTask run, SyncStep step, Gig gig, StepOutcome outcome) {
        Instant now = clock.instant();
        String label = step.type().label();
        switch (outcome.kind()) {
            case DONE -> transactions.inTransaction(() -> outbox.markDone(run.id(),
                    label + (outcome.note() == null || outcome.note().isBlank() ? "" : ": " + outcome.note()), now));
            case FAILED_FOR_GOOD -> outbox.markFailed(run.id(), label + ": " + outcome.note(), now);
            case FAILED -> {
                Optional<Instant> retryAt = SyncRetryPolicy.nextAttempt(run.action(), run.attempts() + 1, false, now);
                if (retryAt.isPresent()) {
                    outbox.markRetry(run.id(), label + ": " + outcome.note(), retryAt.get(), now);
                } else {
                    outbox.markFailed(run.id(), label + ": " + outcome.note(), now);
                }
            }
            case REFUSED -> {
                List<String> reasons = new ArrayList<>(List.of(label + ": " + outcome.note()));
                Optional<StepType> next = run.action().repeatable()
                        ? Workflows.after(step.type()).flatMap(after -> firstTaking(run.platform(), after, gig, reasons))
                        : Optional.empty();
                if (next.isPresent()) {
                    outbox.advance(run.id(), next.get(), String.join("; ", reasons), now);
                } else {
                    outbox.markFailed(run.id(), byHand(run.platform(), reasons), now);
                }
            }
        }
    }

    /** From {@code from} on, the first step the platform has that takes the gig; why the others didn't goes to {@code reasons}. */
    private Optional<StepType> firstTaking(Platform platform, StepType from, Gig gig, List<String> reasons) {
        for (StepType type = from; type != null; type = Workflows.after(type).orElse(null)) {
            SyncStep step = step(platform, type);
            if (step == null) {
                reasons.add(type.label() + ": none for " + SyncRequests.platformName(platform));
                continue;
            }
            Optional<String> refusal = step.refusal(gig);
            if (refusal.isEmpty()) {
                return Optional.of(type);
            }
            reasons.add(type.label() + ": " + refusal.get());
        }
        return Optional.empty();
    }

    private static String byHand(Platform platform, List<String> reasons) {
        return "nothing here can do this on " + SyncRequests.platformName(platform) + " — do it there by hand ("
                + String.join("; ", reasons) + ")";
    }

    private static StepType stepOf(SyncTask task) {
        return task.step() != null ? task.step() : Workflows.of(task.action()).orElseThrow().getFirst();
    }

    private SyncStep step(Platform platform, StepType type) {
        return steps.getOrDefault(platform, Map.of()).get(type);
    }
}
