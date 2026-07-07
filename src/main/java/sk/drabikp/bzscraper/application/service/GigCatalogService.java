package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;

import java.util.List;

/**
 * Manages the local gig catalog (the source of truth) over the {@link GigRepository}.
 * Thin application service — invariants live in the {@link Gig} aggregate, persistence
 * behind the repository port. Cancel/reactivate and edit are lifecycle operations on
 * a stored gig.
 */
public class GigCatalogService
        implements SaveGigUseCase, ListGigsUseCase, DeleteGigUseCase, UpdateGigUseCase, CancelGigUseCase {

    private final GigRepository gigRepository;

    public GigCatalogService(GigRepository gigRepository) {
        this.gigRepository = gigRepository;
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
    public void delete(GigId id) {
        gigRepository.deleteById(id);
    }

    @Override
    public void update(GigId originalId, Gig updated) {
        // If the edit moved the gig's identity (date/venue changed), drop the old row.
        if (!updated.id().equals(originalId)) {
            gigRepository.deleteById(originalId);
        }
        gigRepository.save(updated);
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
