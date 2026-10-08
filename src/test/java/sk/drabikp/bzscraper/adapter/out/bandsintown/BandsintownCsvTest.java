package sk.drabikp.bzscraper.adapter.out.bandsintown;

import com.opencsv.CSVReader;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Address;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Location;
import sk.drabikp.bzscraper.domain.model.Slot;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BandsintownCsvTest {

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private static List<String[]> parse(String csv) throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader(csv))) {
            return reader.readAll();
        }
    }

    @Test
    void new_events_use_the_28_template_columns() throws Exception {
        List<String[]> rows = parse(BandsintownCsv.newEvents(List.of(gig), "Eufory (Band)", true));

        assertThat(rows.get(0)).containsExactly(BandsintownCsv.TEMPLATE_HEADER);
        assertThat(rows.get(1)).hasSize(28);
        assertThat(rows.get(1)[0]).isEqualTo("Eufory (Band)");
        assertThat(rows.get(1)[20]).isEqualTo("Fest");
    }

    @Test
    void new_events_are_marked_do_not_announce_unless_followers_should_be_notified() throws Exception {
        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", false)).get(1)[25]).isEqualTo("Y");
        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", true)).get(1)[25]).isEmpty();
    }

    @Test
    void updates_append_event_id_and_status_and_are_never_announced() throws Exception {
        List<String[]> rows = parse(BandsintownCsv.updates(Map.of("109006286", gig), "Eufory (Band)"));

        assertThat(rows.get(0)).hasSize(30);
        assertThat(rows.get(0)[28]).isEqualTo("Event Id");
        assertThat(rows.get(0)[29]).isEqualTo("Status");
        assertThat(rows.get(1)[28]).isEqualTo("109006286");
        assertThat(rows.get(1)[29]).isEqualTo("PUBLISHED");
        assertThat(rows.get(1)[25]).isEqualTo("Y");
    }

    @Test
    void the_download_template_leaves_do_not_announce_blank() throws Exception {
        assertThat(parse(BandsintownCsv.template(List.of(gig), "X")).get(1)[25]).isEmpty();
    }

    @Test
    void a_festival_with_the_bands_slot_is_listed_at_the_slot_otherwise_at_the_event() throws Exception {
        ZoneId zone = ZoneId.of("Europe/Bratislava");
        GigSchedule festival = new GigSchedule(ZonedDateTime.of(2026, 8, 27, 12, 0, 0, 0, zone),
                ZonedDateTime.of(2026, 8, 30, 0, 0, 0, 0, zone));
        Gig withSlot = new Gig("Moto Fest", festival.withSlot(new Slot(ZonedDateTime.of(2026, 8, 28, 19, 30, 0, 0, zone),
                ZonedDateTime.of(2026, 8, 28, 20, 45, 0, 0, zone))), gig.location(), List.of(), gig.admission(),
                null, null, null, null, false);
        Gig withoutSlot = new Gig("Moto Fest", festival, gig.location(), List.of(), gig.admission(),
                null, null, null, null, false);

        assertThat(Arrays.copyOfRange(parse(BandsintownCsv.newEvents(List.of(withSlot), "X", false)).get(1), 8, 12))
                .containsExactly("2026-08-28", "19:30", "2026-08-28", "20:45");
        assertThat(Arrays.copyOfRange(parse(BandsintownCsv.newEvents(List.of(withoutSlot), "X", false)).get(1), 8, 12))
                .containsExactly("2026-08-27", "12:00", "2026-08-30", "00:00");
    }

    @Test
    void street_and_postal_code_go_along_so_bandsintown_finds_the_right_town() throws Exception {
        Gig located = new Gig("Fest", gig.schedule(), new Location("Zámecký klub", "Hranice", Country.CZECHIA,
                new Address("Pernštejnské nám. 1", "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735)),
                List.of(), gig.admission(), null, null, null, null, false);

        String[] row = parse(BandsintownCsv.newEvents(List.of(located), "X", false)).get(1);

        assertThat(row[3]).isEqualTo("Pernštejnské nám. 1");
        assertThat(row[4]).isEqualTo("Hranice");
        assertThat(row[6]).isEqualTo("753 01");
        assertThat(parse(BandsintownCsv.newEvents(List.of(gig), "X", false)).get(1)[6]).isEmpty();
    }
}
