package sk.drabikp.bzscraper.domain.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GigDateFilterTest {

    private static GigSummary gig(String id, ZonedDateTime start) {
        return GigSummary.GigSummaryBuilder.aGigSummary()
                .setBzId(id)
                .setStart(start)
                .build();
    }

    @Test
    void returnsEmptyForEmptyInput() {
        DateRange range = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertTrue(GigDateFilter.filter(List.of(), range).isEmpty());
    }

    @Test
    void keepsGigsInsideRangeInclusive() {
        GigSummary before = gig("a", ZonedDateTime.of(2025, 12, 31, 20, 0, 0, 0, ZoneOffset.UTC));
        GigSummary startBoundary = gig("b", ZonedDateTime.of(2026, 1, 1, 20, 0, 0, 0, ZoneOffset.UTC));
        GigSummary inside = gig("c", ZonedDateTime.of(2026, 6, 15, 20, 0, 0, 0, ZoneOffset.UTC));
        GigSummary endBoundary = gig("d", ZonedDateTime.of(2026, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC));
        GigSummary after = gig("e", ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        DateRange range = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        List<GigSummary> filtered = GigDateFilter.filter(List.of(before, startBoundary, inside, endBoundary, after), range);

        assertEquals(List.of(startBoundary, inside, endBoundary), filtered);
    }

    @Test
    void dropsGigsWithNullStart() {
        GigSummary nullStart = gig("a", null);
        GigSummary inside = gig("b", ZonedDateTime.of(2026, 6, 15, 20, 0, 0, 0, ZoneOffset.UTC));

        DateRange range = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(List.of(inside), GigDateFilter.filter(List.of(nullStart, inside), range));
    }
}
