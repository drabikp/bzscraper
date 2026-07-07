package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.GigId;

/** Marks a catalog gig cancelled, or reactivates a cancelled one. */
public interface CancelGigUseCase {

    void cancel(GigId id);

    void reactivate(GigId id);
}
