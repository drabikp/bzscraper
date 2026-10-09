package sk.drabikp.bzscraper.gig.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigDraftTest {

    private static GigDraft draft(LocalTime time, LocalTime endTime, EntryType entry, String price) {
        return new GigDraft("Fest", LocalDate.of(2026, 10, 17), time, null, endTime, null, null, null,
                "Klub 007", "Košice", Country.SLOVAKIA, null, "040 01", "okres Košice", "Košický kraj", 48.7, 21.2,
                List.of("Kinless", " "), entry, price, null, null, null, null, false);
    }

    @Test
    void a_gig_and_its_draft_say_the_same_and_a_night_that_runs_past_midnight_ends_the_next_day() {
        Gig gig = draft(LocalTime.of(20, 30), LocalTime.of(2, 0), EntryType.PAID, "8 €").toGig();

        assertThat(gig.schedule().start().getZone().getId()).isEqualTo("Europe/Bratislava");
        assertThat(gig.schedule().end().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(gig.lineup()).containsExactly("Kinless");
        assertThat(gig.location().address().district()).isEqualTo("okres Košice");
        assertThat(GigDraft.of(gig).toGig()).isEqualTo(gig);
    }

    @Test
    void what_is_missing_is_named() {
        assertThat(draft(null, null, EntryType.PAID, " ").problems()).containsExactly("time", "price");
        assertThat(draft(LocalTime.NOON, null, EntryType.FREE, null).problems()).isEmpty();
    }
}
