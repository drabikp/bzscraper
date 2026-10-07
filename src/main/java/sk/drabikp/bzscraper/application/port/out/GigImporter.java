package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.List;

/**
 * Reads all of the band's gigs on one platform — upcoming and past — each with the
 * platform's id for it, so they can be imported into the catalog and linked to the
 * existing events. One implementation per importable platform; the import use case
 * collects them all via an injected {@code List}.
 */
public interface GigImporter {

    Platform platform();

    /**
     * @throws IllegalStateException with a user-facing reason when the platform can't be read
     */
    List<ImportedGig> importGigs();
}
