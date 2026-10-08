package sk.drabikp.bzscraper.domain.model;

import java.time.Instant;
import java.util.List;

/** The calendar as last read ({@code lastRead}, null if never), event by event. */
public record CalendarOverview(Instant lastRead, List<CalendarRow> rows) {

    public CalendarOverview {
        rows = List.copyOf(rows);
    }
}
