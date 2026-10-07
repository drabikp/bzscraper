package sk.drabikp.bzscraper.domain.service;

import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.GigSummary;
import sk.drabikp.bzscraper.domain.model.Location;

import java.util.Optional;

/**
 * Maps a scraped {@link GigSummary} (read model) to a {@link Gig} aggregate (write
 * model). This is the validity boundary: a scraped gig missing the pieces the
 * aggregate requires (title, start, city) yields {@link Optional#empty()} rather
 * than an invalid {@code Gig}. Parses country out of the scraped city string
 * ("Hořice, ČR" -> "Hořice" + {@link Country#CZECHIA}) and infers admission from the
 * free-text entry fee. A gig Bandzone shows as cancelled stays cancelled.
 */
public final class GigSummaryToGigMapper {

    private GigSummaryToGigMapper() {
    }

    public static Optional<Gig> toGig(GigSummary summary) {
        if (summary.start() == null || summary.title() == null || summary.title().isBlank()) {
            return Optional.empty();
        }
        String city = stripCountry(summary.city());
        if (city.isBlank()) {
            return Optional.empty();
        }
        Location location = new Location(summary.venue(), city, resolveCountry(summary.city()));
        GigSchedule schedule = new GigSchedule(summary.start(), summary.end());
        Gig gig = Gig.create(summary.title(), schedule, location, summary.bands(),
                resolveAdmission(summary.entryFee()), null, null, null, null);
        return Optional.of(summary.isCancelled() ? gig.cancel() : gig);
    }

    private static Country resolveCountry(String city) {
        if (city == null) {
            return null;
        }
        if (city.contains(", ČR")) {
            return Country.CZECHIA;
        }
        if (city.contains(", SK")) {
            return Country.SLOVAKIA;
        }
        return null;
    }

    private static String stripCountry(String city) {
        if (city == null) {
            return "";
        }
        return city.replace(", ČR", "").replace(", SK", "").trim();
    }

    private static Admission resolveAdmission(String entryFee) {
        if (entryFee == null || entryFee.isBlank() || entryFee.toLowerCase().contains("zdarma")) {
            return Admission.free();
        }
        return Admission.paid(entryFee);
    }
}
