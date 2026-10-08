package sk.drabikp.bzscraper.domain.model;

import java.util.List;

/**
 * Where a calendar event stands against the catalog. {@link State#LINKED}: the event is
 * linked to {@code gig}, and {@code differences} lists what the calendar now says
 * differently about an upcoming gig. {@link State#SAME_DAY}: not linked, but the catalog
 * has {@code sameDay} gigs on the show's day. {@link State#MISSING}: nothing in the catalog.
 */
public record CatalogMatch(State state, Gig gig, List<Gig> sameDay, List<Difference> differences) {

    public enum State { LINKED, SAME_DAY, MISSING }

    /** What the calendar says differently about a linked gig, as an action the user can take. */
    public record Difference(Kind kind, String text) {
    }

    public enum Kind {
        /** The show is on another day now. */
        DATE,
        /** The show time in the notes moved. */
        SHOW_TIME,
        /** The calendar says cancelled, the catalog doesn't. */
        CANCELLED,
        /** The event is gone from the calendar. */
        REMOVED
    }

    public CatalogMatch {
        sameDay = sameDay == null ? List.of() : List.copyOf(sameDay);
        differences = differences == null ? List.of() : List.copyOf(differences);
    }

    public boolean has(Kind kind) {
        return differences.stream().anyMatch(d -> d.kind() == kind);
    }
}
