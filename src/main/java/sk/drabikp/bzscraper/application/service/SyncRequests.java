package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * What a catalog change puts in the sync outbox. Called INSIDE the caller's transaction
 * (outbox pattern: the change and its tasks commit together); {@link #signal()} after it
 * commits. A task reads the gig's state when it runs, so:
 * <ul>
 *   <li>an update is not queued again while one (or the publish) is still pending —
 *       that one will carry the newest details;</li>
 *   <li>deleting a gig discards its pending tasks and queues one delete per platform the
 *       gig is actually on;</li>
 *   <li>a cancel and a reactivate that both haven't run yet cancel out.</li>
 * </ul>
 */
public class SyncRequests {

    private final SyncOutbox outbox;
    private final PublishedGigStore publishedGigStore;
    private final SyncTrigger trigger;
    private final SyncNotifier notifier;
    private final Clock clock;

    public SyncRequests(SyncOutbox outbox, PublishedGigStore publishedGigStore, SyncTrigger trigger,
                        SyncNotifier notifier, Clock clock) {
        this.outbox = outbox;
        this.publishedGigStore = publishedGigStore;
        this.trigger = trigger;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Tells the worker and the pages; call after the transaction committed and only if something was queued. */
    void signal(QueueResult result) {
        if (!result.queued().isEmpty()) {
            trigger.wake();
            notifier.changed();
        }
    }

    QueueResult update(Gig gig) {
        List<SyncTask> queued = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            boolean pendingPublish = pending(gig.id(), platform, SyncAction.PUBLISH);
            if (!publishedGigStore.isPublished(platform, gig.id()) && !pendingPublish) {
                continue;
            }
            if (!pendingPublish && !pending(gig.id(), platform, SyncAction.UPDATE)) {
                queued.add(enqueue(gig, platform, SyncAction.UPDATE));
            }
        }
        return new QueueResult(queued, List.of());
    }

    QueueResult cancel(Gig gig) {
        return onPublishedPlatforms(gig, SyncAction.CANCEL, SyncAction.REACTIVATE);
    }

    QueueResult reactivate(Gig gig) {
        return onPublishedPlatforms(gig, SyncAction.REACTIVATE, SyncAction.CANCEL);
    }

    QueueResult delete(GigId id, String label) {
        List<SyncTask> queued = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            outbox.supersede(id, platform, "the gig was deleted", clock.instant());
            if (publishedGigStore.isPublished(platform, id)) {
                queued.add(outbox.enqueue(id, label, platform, SyncAction.DELETE, clock.instant()));
            }
        }
        return new QueueResult(queued, List.of());
    }

    QueueResult publish(Platform platform, Gig gig) {
        Function<String, QueueResult> skipped = why ->
                new QueueResult(List.of(), List.of(gig.title() + " on " + platformName(platform) + ": " + why));
        if (gig.cancelled()) {
            return skipped.apply("cancelled");
        }
        if (publishedGigStore.isPublished(platform, gig.id())) {
            return skipped.apply("already published");
        }
        if (outbox.openFor(gig.id()).stream()
                .anyMatch(t -> t.platform() == platform && t.action() == SyncAction.PUBLISH)) {
            return skipped.apply("already being published");
        }
        return new QueueResult(List.of(enqueue(gig, platform, SyncAction.PUBLISH)), List.of());
    }

    void move(GigId from, Gig to) {
        if (!from.equals(to.id())) {
            outbox.move(from, to.id(), SyncTask.labelOf(to));
        }
    }

    /**
     * Queues {@code action} on every platform the gig is on — unless the {@code opposite}
     * change is still pending there: the platform never saw it, so both are dropped and an
     * update brings the copy to the gig's current details instead.
     */
    private QueueResult onPublishedPlatforms(Gig gig, SyncAction action, SyncAction opposite) {
        List<SyncTask> queued = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            if (!publishedGigStore.isPublished(platform, gig.id())) {
                continue;
            }
            if (pending(gig.id(), platform, opposite)) {
                outbox.supersede(gig.id(), platform, "undone by " + action.verb() + " before it ran", clock.instant());
                queued.add(enqueue(gig, platform, SyncAction.UPDATE));
            } else {
                queued.add(enqueue(gig, platform, action));
            }
        }
        return new QueueResult(queued, List.of());
    }

    private boolean pending(GigId id, Platform platform, SyncAction action) {
        return outbox.openFor(id).stream().anyMatch(t ->
                t.platform() == platform && t.action() == action && t.status() == SyncStatus.PENDING);
    }

    private SyncTask enqueue(Gig gig, Platform platform, SyncAction action) {
        return outbox.enqueue(gig.id(), SyncTask.labelOf(gig), platform, action, clock.instant());
    }

    static String platformName(Platform platform) {
        return switch (platform) {
            case BANDZONE -> "Bandzone";
            case BANDSINTOWN -> "Bandsintown";
        };
    }
}
