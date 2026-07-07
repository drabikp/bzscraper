package sk.drabikp.bzscraper.domain.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Value object: when a gig happens. A start is required; an end is optional but,
 * when present, must not be before the start. Immutable and self-validating —
 * an invalid schedule cannot exist.
 */
public record GigSchedule(ZonedDateTime start, ZonedDateTime end) {

    public GigSchedule {
        if (start == null) {
            throw new IllegalArgumentException("gig start is required");
        }
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("gig end must not be before start");
        }
    }

    public static GigSchedule startingAt(ZonedDateTime start) {
        return new GigSchedule(start, null);
    }

    public LocalDate startDate() {
        return start.toLocalDate();
    }

    public boolean hasEnd() {
        return end != null;
    }
}
