package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.application.port.out.GigProvider;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GigQueryServiceTest {

    private static GigSummary gig(String id, ZonedDateTime start) {
        return GigSummary.GigSummaryBuilder.aGigSummary().setBzId(id).setStart(start).build();
    }

    @Test
    void byBandDelegatesToProvider() {
        GigSummary a = gig("a", ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        GigProvider provider = slug -> {
            assertEquals("eufory", slug);
            return List.of(a);
        };

        GigQueryService service = new GigQueryService(provider);

        assertEquals(List.of(a), service.byBand("eufory"));
    }

    @Test
    void byBandBetweenFiltersByDateRange() {
        GigSummary inside = gig("in", ZonedDateTime.of(2026, 6, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        GigSummary outside = gig("out", ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        GigProvider provider = slug -> List.of(inside, outside);

        GigQueryService service = new GigQueryService(provider);
        DateRange range = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(List.of(inside), service.byBandBetween("eufory", range));
    }
}
