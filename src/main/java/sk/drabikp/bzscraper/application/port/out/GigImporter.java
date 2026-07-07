package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.List;

/**
 * Reads the current gigs from one platform as domain {@link Gig}s, so they can be
 * reconciled against the local catalog. One implementation per importable platform;
 * the import use case collects them all via an injected {@code List}.
 */
public interface GigImporter {

    Platform platform();

    List<Gig> importGigs();
}
