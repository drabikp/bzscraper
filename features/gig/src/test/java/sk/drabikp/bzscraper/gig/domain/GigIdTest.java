package sk.drabikp.bzscraper.gig.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GigIdTest {

    @Test
    void the_key_stands_for_the_same_identity_even_for_a_venue_with_the_separator_in_it() {
        GigId id = new GigId(LocalDate.of(2026, 10, 1), "klub | 007");
        GigId byCity = new GigId(LocalDate.of(2026, 10, 2), "@hranice");

        assertThat(id.key()).isEqualTo("2026-10-01|klub | 007");
        assertThat(GigId.fromKey(id.key())).isEqualTo(id);
        assertThat(GigId.fromKey(byCity.key())).isEqualTo(byCity);
    }
}
