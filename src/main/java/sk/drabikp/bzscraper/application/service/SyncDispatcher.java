package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.DispatchSyncUseCase;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformCapabilities;
import sk.drabikp.bzscraper.domain.model.PlatformSupport;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncTask;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;
import sk.drabikp.bzscraper.domain.service.SyncRetryPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the sync outbox, one task at a time; actions that have a workflow (update) are
 * handed to the {@link WorkflowEngine}. Runs the rest of the outbox, one task at a time (the platforms are driven by a browser, one
 * at a time). Each task works from the state at the time it RUNS — the gig as the
 * catalog has it now, the platform id as recorded now — so whatever changed since it was
 * queued is what reaches the platform, and a task whose work became moot ends DONE with
 * a note ("not published there", "the gig was deleted"). A platform's due publishes run
 * as one batch (one browser session). Failures are retried or left for the user by
 * {@link SyncRetryPolicy}. The platform record changes (new id, forgotten id) commit
 * together with the task's DONE.
 * <p>
 * Per-platform strategies come in as lists; a new platform is new strategy beans only.
 */
public class SyncDispatcher implements DispatchSyncUseCase {

    private final Map<Platform, GigPublisher> publishers = new EnumMap<>(Platform.class);
    private final Map<Platform, GigWithdrawer> withdrawers = new EnumMap<>(Platform.class);
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final SyncOutbox outbox;
    private final Transactions transactions;
    private final SyncNotifier notifier;
    private final Clock clock;
    private final PlatformSupport support;
    private final WorkflowEngine engine;

    /** How one run of a task ended. {@code permanent}: waiting won't help, don't retry. */
    private record Outcome(boolean ok, String message, boolean permanent) {

        static Outcome done(String note) {
            return new Outcome(true, note, false);
        }

        static Outcome failed(String error) {
            return new Outcome(false, error == null || error.isBlank() ? "failed without a reason" : error, false);
        }

        /** Trying again won't help (not supported, refused by the platform, platform switched off). */
        static Outcome permanent(String error) {
            return new Outcome(false, error, true);
        }
    }

    public SyncDispatcher(List<GigPublisher> publishers, List<GigWithdrawer> withdrawers, WorkflowEngine engine,
                          GigRepository gigRepository, PublishedGigStore publishedGigStore, SyncOutbox outbox,
                          Transactions transactions, SyncNotifier notifier, Clock clock) {
        publishers.forEach(p -> register(this.publishers, p.platform(), p, "GigPublisher"));
        withdrawers.forEach(w -> register(this.withdrawers, w.platform(), w, "GigWithdrawer"));
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.outbox = outbox;
        this.transactions = transactions;
        this.notifier = notifier;
        this.clock = clock;
        this.support = AdapterCapabilities.of(publishers, withdrawers);
        this.engine = engine;
    }

    private static <T> void register(Map<Platform, T> map, Platform platform, T strategy, String kind) {
        if (map.put(platform, strategy) != null) {
            throw new IllegalStateException("Two " + kind + "s registered for platform " + platform);
        }
    }

    @Override
    public boolean runNext() {
        Optional<SyncTask> next = outbox.nextDue(clock.instant());
        if (next.isEmpty()) {
            return false;
        }
        SyncTask task = next.get();
        if (engine.handles(task.action())) {
            engine.runFrom(task);
        } else if (task.action() == SyncAction.PUBLISH) {
            publishBatch(task.platform());
        } else {
            if (!start(task)) {
                return true;
            }
            Outcome outcome;
            try {
                outcome = run(task);
            } catch (RuntimeException e) {
                outcome = Outcome.failed(e.getMessage());
            }
            finish(task, outcome, () -> { });
        }
        return true;
    }

    @Override
    public void recoverInterrupted() {
        Instant now = clock.instant();
        for (SyncTask task : outbox.running()) {
            if (task.action().repeatable()) {
                outbox.markRetry(task.id(), "interrupted by a restart — running it again", now, now);
            } else {
                outbox.markFailed(task.id(), "interrupted by a restart — it may or may not have happened on "
                        + SyncRequests.platformName(task.platform()) + "; check there, then Retry or Discard", now);
            }
        }
        notifier.changed();
    }

    private boolean start(SyncTask task) {
        boolean started = outbox.markRunning(task.id(), clock.instant());
        notifier.changed();
        return started;
    }

    private Outcome run(SyncTask task) {
        return switch (task.action()) {
            case UPDATE -> throw new IllegalStateException("updates run as a workflow");
            case CANCEL -> withdraw(task, WithdrawAction.CANCEL);
            case DELETE -> withdraw(task, WithdrawAction.DELETE);
            case REACTIVATE -> reactivate(task);
            case PUBLISH -> throw new IllegalStateException("publishes run as a batch");
        };
    }

    private Outcome withdraw(SyncTask task, WithdrawAction action) {
        Optional<String> ref = publishedGigStore.externalRef(task.platform(), task.gigId());
        if (ref.isEmpty()) {
            return Outcome.done(publishedGigStore.isPublished(task.platform(), task.gigId())
                    ? "no platform id was recorded — remove it there by hand" : "not on the platform");
        }
        GigWithdrawer withdrawer = withdrawers.get(task.platform());
        if (withdrawer == null) {
            return Outcome.permanent("Removing gigs on " + SyncRequests.platformName(task.platform())
                    + " is not supported — remove it there by hand.");
        }
        Optional<Gig> gig = gigRepository.findById(task.gigId());
        if (gig.isPresent() && !support.of(task.platform()).allows(task.action(), gig.get(), clock)) {
            return leftAsItIs(task);
        }
        try {
            withdrawer.withdraw(ref.get(), action);
            return Outcome.done(null);
        } catch (GigWithdrawalException e) {
            return e.permanent() ? Outcome.permanent(e.getMessage()) : Outcome.failed(e.getMessage());
        }
    }

    /** The platform's adapter doesn't take this on a past gig: nothing is tried; the user is told. */
    private Outcome leftAsItIs(SyncTask task) {
        return Outcome.done("left as it is: " + PlatformCapabilities.leftOut(
                SyncRequests.platformName(task.platform()), task.action()));
    }

    /** Bandzone can't un-cancel, so the cancelled copy is deleted and the gig published again. */
    private Outcome reactivate(SyncTask task) {
        Optional<Gig> gig = gigRepository.findById(task.gigId());
        if (gig.isEmpty()) {
            return Outcome.done("the gig is no longer in the catalog");
        }
        if (gig.get().cancelled()) {
            return Outcome.done("cancelled again before this ran — nothing to reactivate");
        }
        Platform platform = task.platform();
        Optional<String> oldRef = publishedGigStore.externalRef(platform, task.gigId());
        GigWithdrawer withdrawer = withdrawers.get(platform);
        GigPublisher publisher = publishers.get(platform);
        if (withdrawer == null || publisher == null || oldRef.isEmpty()) {
            return Outcome.permanent("Reactivating gigs on " + SyncRequests.platformName(platform)
                    + " is not supported — reactivate it there by hand.");
        }
        if (!support.of(platform).allows(SyncAction.REACTIVATE, gig.get(), clock)) {
            return leftAsItIs(task);
        }
        // Delete first: Bandzone would otherwise list two copies of the same gig.
        try {
            withdrawer.withdraw(oldRef.get(), WithdrawAction.DELETE);
        } catch (GigWithdrawalException e) {
            return Outcome.failed("could not remove the cancelled copy: " + e.getMessage());
        }
        // The old copy is gone; from here on, no record means a later Publish re-creates it.
        publishedGigStore.remove(platform, task.gigId());

        PublishResult created;
        try {
            created = publisher.publishNew(List.of(gig.get())).getFirst();
        } catch (RuntimeException e) {
            return Outcome.failed("cancelled copy removed, but re-creating failed (" + e.getMessage()
                    + ") — publish the gig again");
        }
        if (created.status() != PublishStatus.PUBLISHED) {
            return Outcome.failed("cancelled copy removed, but re-creating failed (" + created.detail()
                    + ") — publish the gig again");
        }
        publishedGigStore.record(platform, task.gigId(), created.externalRef());
        return Outcome.done(created.detail());
    }

    /** Every due publish on the platform in one go: one browser session for the batch. */
    private void publishBatch(Platform platform) {
        List<SyncTask> batch = outbox.due(platform, SyncAction.PUBLISH, clock.instant());
        Map<SyncTask, Gig> toPublish = new LinkedHashMap<>();
        for (SyncTask task : batch) {
            if (!start(task)) {
                continue;
            }
            Optional<Gig> gig = gigRepository.findById(task.gigId());
            if (gig.isEmpty()) {
                finish(task, Outcome.done("the gig was deleted before it was published"), () -> { });
            } else if (gig.get().cancelled()) {
                finish(task, Outcome.done("the gig was cancelled before it was published"), () -> { });
            } else if (publishedGigStore.isPublished(platform, task.gigId())) {
                finish(task, Outcome.done("already published"), () -> { });
            } else {
                toPublish.put(task, gig.get());
            }
        }
        if (toPublish.isEmpty()) {
            return;
        }
        GigPublisher publisher = publishers.get(platform);
        if (publisher == null) {
            toPublish.keySet().forEach(t -> finish(t, Outcome.permanent("Publishing on "
                    + SyncRequests.platformName(platform) + " is not supported."), () -> { }));
            return;
        }

        List<PublishResult> results;
        try {
            results = publisher.publishNew(new ArrayList<>(toPublish.values()));
        } catch (RuntimeException e) {
            toPublish.keySet().forEach(t -> finish(t, Outcome.failed(e.getMessage()), () -> { }));
            return;
        }
        for (Map.Entry<SyncTask, Gig> entry : toPublish.entrySet()) {
            SyncTask task = entry.getKey();
            Optional<PublishResult> result = results.stream()
                    .filter(r -> r.gig().id().equals(entry.getValue().id())).findFirst();
            if (result.isEmpty()) {
                finish(task, Outcome.failed("the platform gave no result for this gig"), () -> { });
            } else if (result.get().status() == PublishStatus.PUBLISHED) {
                String ref = result.get().externalRef();
                finish(task, Outcome.done(result.get().detail()),
                        () -> publishedGigStore.record(platform, task.gigId(), ref));
            } else {
                finish(task, Outcome.failed(result.get().detail()), () -> { });
            }
        }
    }

    /**
     * Settles a task. A success commits {@code onSuccess} (the platform record change)
     * together with DONE; for a delete, that is forgetting the platform's id.
     */
    private void finish(SyncTask task, Outcome outcome, Runnable onSuccess) {
        Instant now = clock.instant();
        if (outcome.ok()) {
            transactions.inTransaction(() -> {
                onSuccess.run();
                if (task.action() == SyncAction.DELETE) {
                    publishedGigStore.remove(task.platform(), task.gigId());
                }
                outbox.markDone(task.id(), outcome.message(), now);
            });
        } else {
            int attemptsMade = task.attempts() + 1;
            Optional<Instant> retryAt = SyncRetryPolicy.nextAttempt(task.action(), attemptsMade, outcome.permanent(), now);
            if (retryAt.isPresent()) {
                outbox.markRetry(task.id(), outcome.message(), retryAt.get(), now);
            } else {
                outbox.markFailed(task.id(), outcome.message(), now);
            }
        }
        notifier.changed();
    }
}
