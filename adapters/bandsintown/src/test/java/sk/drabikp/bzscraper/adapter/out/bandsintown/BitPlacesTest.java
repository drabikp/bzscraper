package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Venue suggestions as Bandsintown's search returned them (2026-10-08). */
class BitPlacesTest {

    private static BitPlace place(String name, String description) {
        return BitPlace.of(Map.of("place_id", "x", "name", name, "description", description));
    }

    private static final BitPlace GARDEN_KOSICE =
            place("Secret Garden (Skrytý Dvor)", "Secret Garden (Skrytý Dvor), Moyzesova, Košice, Slovakia");
    private static final BitPlace GARDEN_BRATISLAVA =
            place("Secret Garden", "Secret Garden, Obchodná, Bratislava, Slovakia");
    private static final BitPlace KLUB_KOSICE = place("Collosseum", "Collosseum, Južná trieda, Košice, Slovakia");

    @Test
    void only_a_place_in_the_gigs_town_named_like_the_venue_first() {
        assertThat(BitPlaces.choose(List.of(GARDEN_BRATISLAVA, KLUB_KOSICE, GARDEN_KOSICE), "Secret Garden (Skrytý Dvor)",
                "Košice")).isSameAs(GARDEN_KOSICE);
        assertThat(BitPlaces.choose(List.of(GARDEN_BRATISLAVA, KLUB_KOSICE), null, "Kosice")).isSameAs(KLUB_KOSICE);
    }

    @Test
    void nothing_in_the_town_is_nothing() {
        assertThat(BitPlaces.choose(List.of(GARDEN_BRATISLAVA), "Secret Garden", "Košice")).isNull();
        assertThat(BitPlaces.choose(List.of(), "Secret Garden", "Košice")).isNull();
    }
}
