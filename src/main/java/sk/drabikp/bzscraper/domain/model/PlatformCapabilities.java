package sk.drabikp.bzscraper.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * What one platform can do with PAST events, as its adapters declare it (never
 * configured): Bandsintown, for one, lists published past events but refuses edits to
 * them, and its cancel is a removal. Work a platform doesn't take is not even tried — the
 * user is told to do it there by hand.
 */
public record PlatformCapabilities(boolean publishPast, boolean editPast, boolean cancelPast, boolean removePast) {

    public static final PlatformCapabilities ALL = new PlatformCapabilities(true, true, true, true);

    /** Whether the platform takes {@code action} on a gig that is already over. */
    public boolean allowsPast(SyncAction action) {
        return switch (action) {
            case PUBLISH -> publishPast;
            case UPDATE -> editPast;
            case CANCEL -> cancelPast;
            case DELETE -> removePast;
            case REACTIVATE -> removePast && publishPast;   // the cancelled copy goes, a new one comes
        };
    }

    /** Whether the platform takes {@code action} on {@code gig} today. */
    public boolean allows(SyncAction action, Gig gig, Clock clock) {
        return allowsPast(action) || !isPast(gig, clock);
    }

    /** The band's show day is before today (in the gig's own time zone). */
    public static boolean isPast(Gig gig, Clock clock) {
        ZonedDateTime show = gig.schedule().showStart();
        return show.toLocalDate().isBefore(LocalDate.now(clock.withZone(show.getZone())));
    }

    /** What the user should know when {@code action} was left out for a past gig. */
    public static String leftOut(String platform, SyncAction action) {
        return switch (action) {
            case PUBLISH -> platform + " doesn't take past events";
            case UPDATE -> platform + " doesn't take changes to past events — change it there by hand if needed";
            case CANCEL -> platform + " past events can't be cancelled from here — do it there by hand if needed";
            case DELETE -> platform + " past events can't be removed from here — delete it there by hand";
            case REACTIVATE -> platform + " past events can't be reactivated from here";
        };
    }
}
