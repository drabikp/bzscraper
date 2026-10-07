package sk.drabikp.bzscraper.adapter.out.bandsintown;

import com.opencsv.CSVReader;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.io.StringReader;
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
}
