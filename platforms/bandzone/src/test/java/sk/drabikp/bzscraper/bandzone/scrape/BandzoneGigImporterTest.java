package sk.drabikp.bzscraper.bandzone.scrape;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzoneProperties;
import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BandzoneGigImporterTest {

    private final GigProvider provider = mock(GigProvider.class);

    private static GigSummary summary(String bzId, boolean cancelled) {
        return GigSummary.GigSummaryBuilder.aGigSummary()
                .setBzId(bzId)
                .setTitle("Fest")
                .setStart(ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")))
                .setCity("Praha, ČR")
                .setVenue("Klub 007")
                .setBands(List.of())
                .setIsCancelled(cancelled)
                .build();
    }

    private static BandzoneProperties band(String slug) {
        return new BandzoneProperties(null, null, null, slug, null);
    }

    @Test
    void each_gig_keeps_its_bandzone_concert_id_and_cancelled_state() throws Exception {
        when(provider.findByBand("eufory")).thenReturn(List.of(summary("556604", false), summary("556605", true)));

        List<ImportedGig> gigs = new BandzoneGigImporter(provider, band("eufory")).importGigs();

        assertThat(gigs).extracting(ImportedGig::externalRef).containsExactly("556604", "556605");
        assertThat(gigs).allMatch(g -> g.platform() == BandzonePlatform.PLATFORM);
        assertThat(gigs.get(1).gig().cancelled()).isTrue();
    }

    @Test
    void a_concert_listed_twice_is_imported_once() throws Exception {
        when(provider.findByBand("eufory")).thenReturn(List.of(summary("556604", false), summary("556604", false)));

        assertThat(new BandzoneGigImporter(provider, band("eufory")).importGigs()).hasSize(1);
    }

    @Test
    void needs_the_band_slug() {
        assertThatThrownBy(() -> new BandzoneGigImporter(provider, band("")).importGigs())
                .isInstanceOf(BandzoneUploadException.class);
    }
}
