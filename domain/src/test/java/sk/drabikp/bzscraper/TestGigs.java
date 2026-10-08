package sk.drabikp.bzscraper;

import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Test fixtures for building valid {@link Gig} aggregates concisely. */
public final class TestGigs {

    private TestGigs() {
    }

    public static Gig gig(String title, String venue) {
        return gig(title, venue, ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));
    }

    public static Gig gig(String title, String venue, ZonedDateTime start) {
        return Gig.create(title, GigSchedule.startingAt(start),
                new Location(venue, "Praha", Country.CZECHIA), List.of(), Admission.free(),
                null, null, null, null);
    }
}
