package sk.drabikp.bzscraper.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Aggregate root: a gig to publish. Always valid — the compact constructor enforces
 * the invariants (title, schedule, location and admission are required), so an
 * invalid {@code Gig} cannot exist and downstream code needs no defensive checks.
 *
 * Composed of value objects ({@link GigSchedule}, {@link Location}, {@link Admission});
 * identified by {@link GigId} (its natural key). Immutable: state-changing behaviour
 * ({@link #rescheduledTo}, {@link #cancel}) returns a new instance. Descriptive
 * attributes (description, links, poster) are optional and nullable.
 */
public record Gig(
        String title,
        GigSchedule schedule,
        Location location,
        List<String> lineup,
        Admission admission,
        String description,
        String facebookUrl,
        String ticketUrl,
        String posterImageUrl,
        boolean cancelled
) {
    public Gig {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("gig title is required");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("gig schedule is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("gig location is required");
        }
        if (admission == null) {
            throw new IllegalArgumentException("gig admission is required");
        }
        title = title.trim();
        lineup = lineup == null ? List.of() : List.copyOf(lineup);
    }

    /** Factory for a fresh (not cancelled) gig. */
    public static Gig create(String title, GigSchedule schedule, Location location, List<String> lineup,
                             Admission admission, String description, String facebookUrl,
                             String ticketUrl, String posterImageUrl) {
        return new Gig(title, schedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, false);
    }

    public GigId id() {
        return GigId.of(this);
    }

    /** The band's show day ({@link GigSchedule#showStart}) is before today, in the gig's own time zone. */
    public boolean isPast(Clock clock) {
        ZonedDateTime show = schedule.showStart();
        return show.toLocalDate().isBefore(LocalDate.now(clock.withZone(show.getZone())));
    }

    public Gig rescheduledTo(GigSchedule newSchedule) {
        return new Gig(title, newSchedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, cancelled);
    }

    public Gig cancel() {
        return new Gig(title, schedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, true);
    }

    public Gig reactivate() {
        return new Gig(title, schedule, location, lineup, admission, description, facebookUrl,
                ticketUrl, posterImageUrl, false);
    }
}
