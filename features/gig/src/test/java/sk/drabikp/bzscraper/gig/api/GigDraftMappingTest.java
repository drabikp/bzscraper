package sk.drabikp.bzscraper.gig.api;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.EntryType;
import sk.drabikp.bzscraper.gig.domain.GigDraft;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigDraftMappingTest {

    private final GigDraft draft = new GigDraft("Fest", LocalDate.of(2026, 10, 17), LocalTime.of(20, 30), null,
            LocalTime.of(2, 0), LocalDate.of(2026, 10, 17), LocalTime.of(22, 0), LocalTime.of(23, 15),
            "Klub 007", "Luková", Country.CZECHIA, null, null, "okres Ústí nad Orlicí", "Pardubický kraj",
            49.876, 16.603, List.of("Other band"), EntryType.PAID, "10 €", "Notes", null, null, null, false);

    @Test
    void a_draft_goes_to_the_api_and_back_unchanged() {
        GigDraftJson json = GigDraftMapping.toJson(draft);

        assertThat(json.getTime()).isEqualTo("20:30");
        assertThat(json.getCountry()).isEqualTo(CountryJson.CZECHIA);
        assertThat(json.getEntry()).isEqualTo(EntryTypeJson.PAID);
        assertThat(GigDraftMapping.toDraft(json)).isEqualTo(draft);
    }

    @Test
    void times_with_seconds_and_missing_fields_are_read_too() {
        GigDraftJson json = GigDraftMapping.toJson(draft).time("20:30:00").endTime("").country(null).entry(null)
                .cancelled(null);

        GigDraft read = GigDraftMapping.toDraft(json);

        assertThat(read.time()).isEqualTo(LocalTime.of(20, 30));
        assertThat(read.endTime()).isNull();
        assertThat(read.country()).isNull();
        assertThat(read.entry()).isEqualTo(EntryType.FREE);
        assertThat(read.cancelled()).isFalse();
    }
}
