package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.ResyncGigUseCase;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Queues an update of every platform copy of the gigs, to their current catalog details. */
public class GigResyncService implements ResyncGigUseCase {

    private final GigRepository gigRepository;
    private final SyncRequests sync;
    private final Transactions transactions;

    public GigResyncService(GigRepository gigRepository, SyncRequests sync, Transactions transactions) {
        this.gigRepository = gigRepository;
        this.sync = sync;
        this.transactions = transactions;
    }

    @Override
    public QueueResult resync(Collection<GigId> gigs) {
        QueueResult result = transactions.computeInTransaction(() -> {
            List<SyncTask> queued = new ArrayList<>();
            List<String> notQueued = new ArrayList<>();
            for (GigId id : gigs) {
                Optional<Gig> gig = gigRepository.findById(id);
                if (gig.isEmpty()) {
                    continue;
                }
                if (sync.running(id)) {
                    notQueued.add(SyncTask.labelOf(gig.get()) + ": its platform work is running — re-sync it when "
                            + "that is done");
                    continue;
                }
                QueueResult one = sync.update(gig.get());
                queued.addAll(one.queued());
                notQueued.addAll(one.notQueued());
                if (one.queued().isEmpty() && one.notQueued().isEmpty()) {
                    notQueued.add(gig.get().title() + ": not on any platform, or an update is already waiting");
                }
            }
            return new QueueResult(queued, notQueued);
        });
        sync.signal(result);
        return result;
    }
}
