package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;

/** Adds or updates a gig in the local catalog (the source of truth). Upsert by identity. */
public interface SaveGigUseCase {

    void save(Gig gig);
}
