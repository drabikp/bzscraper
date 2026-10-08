package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Queues gigs for publishing (one outbox task per gig and platform, all in one
 * transaction). The sync worker publishes them — a platform's pending publishes in one
 * batch — and records each platform's id for the gig. Idempotent: gigs already
 * published or being published there are left out.
 */
public class GigPublishingService implements PublishGigsUseCase {

    private final SyncRequests sync;
    private final Transactions transactions;

    public GigPublishingService(SyncRequests sync, Transactions transactions) {
        this.sync = sync;
        this.transactions = transactions;
    }

    @Override
    public QueueResult publish(Set<Platform> targets, Collection<Gig> gigs) {
        QueueResult result = transactions.computeInTransaction(() -> {
            List<SyncTask> queued = new ArrayList<>();
            List<String> notQueued = new ArrayList<>();
            for (Platform platform : Platform.values()) {
                if (!targets.contains(platform)) {
                    continue;
                }
                for (Gig gig : gigs) {
                    QueueResult one = sync.publish(platform, gig);
                    queued.addAll(one.queued());
                    notQueued.addAll(one.notQueued());
                }
            }
            return new QueueResult(queued, notQueued);
        });
        sync.signal(result);
        return result;
    }
}
