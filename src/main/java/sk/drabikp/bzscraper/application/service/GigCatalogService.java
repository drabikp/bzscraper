package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the local gig catalog (the source of truth) over the {@link GigRepository}.
 * Thin application service — invariants live in the {@link Gig} aggregate, persistence
 * behind the repository port. Cancel/reactivate and edit are lifecycle operations on
 * a stored gig. An edit that changes the gig's identity also re-keys its published
 * records, in the same transaction, so they never point at a gig that no longer exists.
 */
public class GigCatalogService
        implements SaveGigUseCase, ListGigsUseCase, DeleteGigUseCase, UpdateGigUseCase, CancelGigUseCase,
        ListPublicationsUseCase {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final Transactions transactions;

    public GigCatalogService(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                             Transactions transactions) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.transactions = transactions;
    }

    @Override
    public void save(Gig gig) {
        gigRepository.save(gig);
    }

    @Override
    public List<Gig> allGigs() {
        return gigRepository.findAll();
    }

    @Override
    public List<Gig> gigsStartingWithin(DateRange range) {
        return gigRepository.findStartingWithin(range);
    }

    @Override
    public Map<GigId, Map<Platform, Publication>> publicationsByGig() {
        Map<GigId, Map<Platform, Publication>> byGig = new HashMap<>();
        for (Publication p : publishedGigStore.all()) {
            byGig.computeIfAbsent(p.gigId(), id -> new EnumMap<>(Platform.class)).put(p.platform(), p);
        }
        return byGig;
    }

    @Override
    public void delete(GigId id) {
        gigRepository.deleteById(id);
    }

    @Override
    public void update(GigId originalId, Gig updated) {
        transactions.inTransaction(() -> {
            // If the edit moved the gig's identity (date/venue changed), drop the old row
            // and let the published records follow the gig.
            if (!updated.id().equals(originalId)) {
                gigRepository.deleteById(originalId);
                publishedGigStore.move(originalId, updated.id());
            }
            gigRepository.save(updated);
        });
    }

    @Override
    public void cancel(GigId id) {
        gigRepository.findById(id).ifPresent(gig -> gigRepository.save(gig.cancel()));
    }

    @Override
    public void reactivate(GigId id) {
        gigRepository.findById(id).ifPresent(gig -> gigRepository.save(gig.reactivate()));
    }
}
