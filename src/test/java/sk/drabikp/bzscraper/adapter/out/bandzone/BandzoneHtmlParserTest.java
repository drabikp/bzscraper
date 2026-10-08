package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BandzoneHtmlParserTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static ZonedDateTime at(int y, int m, int d, int h) {
        return ZonedDateTime.of(y, m, d, h, 0, 0, 0, PRAGUE);
    }

    @Test
    void an_end_rendered_with_todays_date_is_the_end_time_of_the_gigs_own_night() {
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2020, 10, 31, 19), at(2026, 10, 8, 22), TODAY))
                .isEqualTo(at(2020, 10, 31, 22));
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2020, 10, 31, 19), at(2026, 10, 8, 2), TODAY))
                .as("past midnight").isEqualTo(at(2020, 11, 1, 2));
    }

    @Test
    void real_ends_are_kept() {
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2026, 8, 27, 12), at(2026, 8, 30, 0), TODAY))
                .isEqualTo(at(2026, 8, 30, 0));
        assertThat(BandzoneHtmlParser.plausibleEnd(at(2026, 10, 5, 12), at(2026, 10, 8, 22), TODAY))
                .as("a festival ending today").isEqualTo(at(2026, 10, 8, 22));
    }
}
