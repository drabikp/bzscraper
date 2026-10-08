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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

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
 *   <li>Outcomes: done (with the platform record change: a created event's id recorded,
 *       a removed one's forgotten — in the same transaction); refused (nothing happened
 *       there) → the next step; failed → this step again later, as {@link SyncRetryPolicy}
 *       allows (never for creates: one may have happened anyway); failed for good → the user.</li>
 *   <li>Reactivating ("remove and create again") is built from the platform's remove and
 *       create steps when it has no step of its own for it.</li>
 *   <li>Work that became moot ends done with a note (the gig was deleted, cancelled or
 *       published meanwhile). The path is logged: "bulk edit: past event → form edit".</li>
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
    private final BooleanSupplier paused;

    public WorkflowEngine(List<SyncStep> steps, GigRepository gigRepository, PublishedGigStore publishedGigStore,
                          SyncOutbox outbox, Transactions transactions, SyncNotifier notifier, Clock clock) {
        this(steps, gigRepository, publishedGigStore, outbox, transactions, notifier, clock, () -> false);
    }

    /** {@code paused}: checked before each batch — the gig in progress finishes, the rest stays queued. */
    public WorkflowEngine(List<SyncStep> steps, GigRepository gigRepository, PublishedGigStore publishedGigStore,
                          SyncOutbox outbox, Transactions transactions, SyncNotifier notifier, Clock clock,
                          BooleanSupplier paused) {
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
        this.paused = paused;
    }

    @Override
    public Optional<String> leftOut(Platform platform, SyncAction action, Gig gig) {
        List<String> reasons = new ArrayList<>();
        return firstTaking(platform, Workflows.of(action).orElseThrow().getFirst(), gig, reasons).isPresent()
                ? Optional.empty() : Optional.of(byHand(platform, reasons));
    }

    /** Runs the work due at {@code task}'s step on its platform — {@code task} and the runs waiting with it. */
    public void runFrom(SyncTask task) {
        Platform platform = task.platform();
        StepType stepType = stepOf(task);
        SyncStep step = step(platform, stepType);
        List<SyncTask> waiting = outbox.due(platform, task.action(), clock.instant()).stream()
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
        for (int from = 0; from < runs.size() && !paused.getAsBoolean(); from += step.batchSize()) {
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
        Optional<String> moot = switch (run.action()) {
            case PUBLISH -> gig == null ? Optional.of("the gig was deleted before it was published")
                    : gig.cancelled() ? Optional.of("the gig was cancelled before it was published")
                    : publishedGigStore.isPublished(platform, run.gigId()) ? Optional.of("already published")
                    : Optional.empty();
            case UPDATE -> gig == null ? Optional.of("the gig is no longer in the catalog")
                    : !publishedGigStore.isPublished(platform, run.gigId())
                    ? Optional.of("not on " + SyncRequests.platformName(platform) + " — nothing to do") : Optional.empty();
            case CANCEL, DELETE -> publishedGigStore.isPublished(platform, run.gigId())
                    ? Optional.empty() : Optional.of("not on the platform");
            case REACTIVATE -> gig == null ? Optional.of("the gig is no longer in the catalog")
                    : gig.cancelled() ? Optional.of("cancelled again before this ran — nothing to reactivate")
                    : Optional.empty();
        };
        if (moot.isPresent()) {
            outbox.markDone(run.id(), moot.get(), now);
            return Optional.empty();
        }
        if (run.action() != SyncAction.PUBLISH && ref == null) {
            outbox.markFailed(run.id(), "no " + SyncRequests.platformName(platform)
                    + " id was recorded — do it there by hand", now);
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        Optional<StepType> where = firstTaking(platform, stepType, gig, reasons);
        if (where.isEmpty()) {
            outbox.markFailed(run.id(), byHand(platform, reasons), now);
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
            case DONE -> transactions.inTransaction(() -> {
                if (outcome.ref() != null) {
                    publishedGigStore.record(run.platform(), run.gigId(), outcome.ref());
                }
                if (run.action() == SyncAction.DELETE) {
                    publishedGigStore.remove(run.platform(), run.gigId());
                }
                outbox.markDone(run.id(),
                        label + (outcome.note() == null || outcome.note().isBlank() ? "" : ": " + outcome.note()), now);
            });
            case FAILED_FOR_GOOD -> outbox.markFailed(run.id(), label + ": " + outcome.note(), now);
            case FAILED -> {
                Optional<Instant> retryAt = SyncRetryPolicy.nextAttempt(run.action(), run.attempts() + 1, false, now);
                if (retryAt.isPresent()) {
                    outbox.markRetry(run.id(), label + ": " + outcome.note(), retryAt.get(), now);
                } else {
                    outbox.markFailed(run.id(), label + ": " + outcome.note()
                            + (run.action().repeatable() ? "" : " — check " + SyncRequests.platformName(run.platform())
                            + ", then Retry or Discard"), now);
                }
            }
            case REFUSED -> {
                // the platform said no and nothing happened there: another way may do it
                List<String> reasons = new ArrayList<>(List.of(label + ": " + outcome.note()));
                Optional<StepType> next = Workflows.after(step.type())
                        .flatMap(after -> firstTaking(run.platform(), after, gig, reasons));
                if (next.isPresent()) {
                    outbox.advance(run.id(), next.get(), String.join("; ", reasons), now);
                } else {
                    outbox.markFailed(run.id(), byHand(run.platform(), reasons), now);
                }
            }
        }
    }

    /**
     * From {@code from} on, the first step the platform has that takes the gig; why the others
     * didn't goes to {@code reasons}. Without the gig (deleted from the catalog), a step is
     * taken as it is — it was checked when the work was queued.
     */
    private Optional<StepType> firstTaking(Platform platform, StepType from, Gig gig, List<String> reasons) {
        for (StepType type = from; type != null; type = Workflows.after(type).orElse(null)) {
            SyncStep step = step(platform, type);
            if (step == null) {
                reasons.add(type.label() + ": none for " + SyncRequests.platformName(platform));
                continue;
            }
            Optional<String> refusal = gig == null ? Optional.empty() : step.refusal(gig);
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

    /** The platform's step of that type — for RECREATE, else built from its remove and create steps. */
    private SyncStep step(Platform platform, StepType type) {
        Map<StepType, SyncStep> own = steps.getOrDefault(platform, Map.of());
        if (own.containsKey(type) || type != StepType.RECREATE) {
            return own.get(type);
        }
        List<SyncStep> removes = Stream.of(StepType.REMOVE, StepType.FORM_REMOVE)
                .map(own::get).filter(Objects::nonNull).toList();
        SyncStep create = own.containsKey(StepType.FORM_CREATE) ? own.get(StepType.FORM_CREATE)
                : own.get(StepType.BULK_CREATE);
        return removes.isEmpty() || create == null ? null : new Recreate(removes, create);
    }

    /**
     * Reactivating where the platform can't un-cancel: the cancelled copy is removed (and its
     * record forgotten at once — from then on, no record means a later Publish creates it),
     * then the gig is created again; the new id is recorded when this is settled. The copy is
     * removed the first of the platform's ways that takes the gig (the list, else the form).
     */
    private final class Recreate implements SyncStep {

        private final List<SyncStep> removes;
        private final SyncStep create;

        Recreate(List<SyncStep> removes, SyncStep create) {
            this.removes = removes;
            this.create = create;
        }

        @Override
        public Platform platform() {
            return create.platform();
        }

        @Override
        public StepType type() {
            return StepType.RECREATE;
        }

        @Override
        public int batchSize() {
            return 1;
        }

        @Override
        public Optional<String> refusal(Gig gig) {
            return removing(gig).isPresent() ? create.refusal(gig) : removes.getFirst().refusal(gig);
        }

        private Optional<SyncStep> removing(Gig gig) {
            return removes.stream().filter(remove -> remove.refusal(gig).isEmpty()).findFirst();
        }

        @Override
        public List<StepOutcome> run(List<Item> items) {
            return items.stream().map(this::recreate).toList();
        }

        private StepOutcome recreate(Item item) {
            SyncStep remove = removing(item.gig()).orElse(removes.getLast());
            StepOutcome removed = remove.run(List.of(item)).getFirst();
            if (removed.kind() != StepOutcome.Kind.DONE) {
                return removed.kind() == StepOutcome.Kind.REFUSED ? removed
                        : new StepOutcome(removed.kind(), "could not remove the cancelled copy: " + removed.note(), null);
            }
            transactions.inTransaction(() -> publishedGigStore.remove(platform(), item.gig().id()));
            StepOutcome created = create.run(List.of(new Item(null, item.gig()))).getFirst();
            if (created.kind() == StepOutcome.Kind.DONE && created.ref() != null) {
                return created;
            }
            return StepOutcome.failedForGood("cancelled copy removed, but re-creating failed (" + created.note()
                    + ") — publish the gig again");
        }
    }
}
