package sk.drabikp.bzscraper.domain.model;

import java.time.LocalDate;

/**
 * Identity of a {@link Gig} — its natural business key: start date + normalized
 * venue. Single-band tool, so no band component is needed. Venue is lowercased,
 * trimmed, and internal whitespace collapsed, so scraped/typed variants of the same
 * venue resolve to the same identity. Same date + clearly different venue = two
 * gigs (a double-header), not one.
 */
public record GigId(LocalDate date, String venue) {

    public static GigId of(Gig gig) {
        return new GigId(gig.schedule().startDate(), normalizeVenue(gig.location().venue()));
    }

    static String normalizeVenue(String venue) {
        if (venue == null) {
            return "";
        }
        return venue.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
