package sk.drabikp.bzscraper.domain.model;

import java.time.LocalDate;

/**
 * Identity of a {@link Gig} — its natural business key: start date + normalized
 * venue. Single-band tool, so no band component is needed. Venue is lowercased,
 * trimmed, and internal whitespace collapsed, so scraped/typed variants of the same
 * venue resolve to the same identity. Same date + clearly different venue = two
 * gigs (a double-header), not one. A gig whose venue is not known yet ("TBA") is
 * identified by its city instead ({@code "@" + city}), so two such gigs on one day in
 * different cities stay two gigs.
 */
public record GigId(LocalDate date, String venue) {

    public static GigId of(Gig gig) {
        String venue = gig.location().venue();
        return new GigId(gig.schedule().startDate(),
                venue != null ? normalizeVenue(venue) : "@" + normalizeVenue(gig.location().city()));
    }

    static String normalizeVenue(String venue) {
        if (venue == null) {
            return "";
        }
        return venue.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
