package sk.drabikp.bzscraper.check.domain;

import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;

/**
 * A way a platform's copy of a published gig no longer matches the catalog — found by
 * reading the platform, not by anything the app did: a copy changed or deleted there by
 * hand, or placed in another town.
 *
 * @param differences what differs, readable ("date: catalog 18 Sep 2026, <platform> 19 Sep 2026"); empty for MISSING
 */
public record Drift(Platform platform, GigId gigId, String gigLabel, String externalRef, Kind kind,
                    List<String> differences) {

    public enum Kind {
        /** The platform no longer lists the event the gig was published as. */
        MISSING,
        /** The platform's copy differs from the catalog. */
        DIFFERENT
    }

    public Drift {
        differences = List.copyOf(differences);
    }
}
