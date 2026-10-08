package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.List;

/** Writes gigs into a file a platform imports by hand (its own format). */
public interface GigExporter {

    Platform platform();

    /** The downloaded file's name, e.g. "gigs.csv". */
    String fileName();

    String mediaType();

    String export(List<Gig> gigs);
}
