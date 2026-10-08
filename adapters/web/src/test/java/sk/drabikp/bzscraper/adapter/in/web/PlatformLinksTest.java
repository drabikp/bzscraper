package sk.drabikp.bzscraper.adapter.in.web;

import sk.drabikp.bzscraper.TestPlatforms;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformLinksTest {

    private final GigId id = TestGigs.gig("A", "Klub 007").id();

    @Test
    void links_to_the_public_page_on_each_platform() {
        assertThat(PlatformLinks.of(TestPlatforms.BANDZONE_TRAITS, new Publication(TestPlatforms.BANDZONE, id, "556604"), false))
                .isEqualTo(new PlatformLinks.Link("Bandzone", "https://bandzone.cz/koncert/556604"));
        assertThat(PlatformLinks.of(TestPlatforms.BANDSINTOWN_TRAITS, new Publication(TestPlatforms.BANDSINTOWN, id, "109006286"), false))
                .isEqualTo(new PlatformLinks.Link("Bandsintown", "https://www.bandsintown.com/e/109006286"));
    }

    @Test
    void a_cancelled_gig_still_links_where_cancelled_events_stay_but_not_where_cancelling_removed_it() {
        assertThat(PlatformLinks.of(TestPlatforms.BANDZONE_TRAITS, new Publication(TestPlatforms.BANDZONE, id, "1"), true).href()).isNotNull();
        assertThat(PlatformLinks.of(TestPlatforms.BANDSINTOWN_TRAITS, new Publication(TestPlatforms.BANDSINTOWN, id, "2"), true))
                .isEqualTo(new PlatformLinks.Link("Bandsintown (removed)", null));
    }

    @Test
    void without_a_platform_id_the_name_is_shown_without_a_link() {
        assertThat(PlatformLinks.of(TestPlatforms.BANDSINTOWN_TRAITS, new Publication(TestPlatforms.BANDSINTOWN, id, null), false))
                .isEqualTo(new PlatformLinks.Link("Bandsintown", null));
    }
}
