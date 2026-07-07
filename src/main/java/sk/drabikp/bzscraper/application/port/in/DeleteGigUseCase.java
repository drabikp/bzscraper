package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.GigId;

/** Removes a gig from the local catalog. */
public interface DeleteGigUseCase {

    void delete(GigId id);
}
