package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.domain.model.Address;
import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Live integration test — drives real Bandzone with a real browser: CREATES a gig on
 * the configured band with every field Bandzone has (end, club venue, poster, paid entry,
 * lineup with a profile band and a profile-less one), EDITS it (free-text venue, other
 * city, a performer removed), then DELETES it (cleanup runs even when a step fails).
 * Skipped unless {@code BZ_LIVE=true}. Reads creds from env so nothing is committed:
 *   BZ_LOGIN, BZ_PASSWORD, BZ_SLUG  (required)
 *   BZ_LINEUP_BAND  a Bandzone band that may be notified (default: none, lineup keeps stubs only)
 *   BZ_KEEP=true    keep the gig for manual inspection (prints its id)
 *   BZ_CHROMIUM (default /usr/bin/chromium), BZ_CHROMEDRIVER (default /usr/bin/chromedriver)
 *
 * Use a test band — the gig is briefly public, and BZ_LINEUP_BAND gets notified.
 */
@EnabledIfEnvironmentVariable(named = "BZ_LIVE", matches = "true")
class SeleniumBandzonePortalClientLiveTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final String STUB = "Bzscraper Testovaci Interpret";

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    @Test
    void creates_edits_and_deletes_a_gig_with_every_field() {
        SeleniumBandzonePortalClient client = new SeleniumBandzonePortalClient(
                env("BZ_BASE_URL", "https://bandzone.cz"),
                env("BZ_LOGIN", ""),
                env("BZ_PASSWORD", ""),
                env("BZ_SLUG", ""),
                Boolean.parseBoolean(env("BZ_HEADLESS", "true")),
                env("BZ_CHROMIUM", "/usr/bin/chromium"),
                env("BZ_CHROMEDRIVER", "/usr/bin/chromedriver"),
                env("BZ_PROFILE", ""),
                env("BZ_PASSWORD_STORE", "auto"));
        String lineupBand = env("BZ_LINEUP_BAND", "");
        boolean keep = Boolean.parseBoolean(env("BZ_KEEP", "false"));

        List<String> fullLineup = lineupBand.isBlank() ? List.of(STUB) : List.of(lineupBand, STUB);
        List<String> editedLineup = lineupBand.isBlank() ? List.of() : List.of(lineupBand);

        Gig gig = new Gig("TEST selenium client (smazat)",
                new GigSchedule(ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, PRAGUE),
                        ZonedDateTime.of(2026, 11, 13, 1, 0, 0, 0, PRAGUE)),
                new Location("Lucerna Music Bar", "Praha", Country.CZECHIA), fullLineup,
                Admission.paid("200 Kč"), "created by the live test", "https://www.facebook.com/events/1",
                null, "https://bandzone.cz/img/default/band-single-small.png", false);
        Gig edited = new Gig("TEST selenium client edited (smazat)",
                GigSchedule.startingAt(ZonedDateTime.of(2026, 11, 14, 21, 30, 0, 0, PRAGUE)),
                // one of three towns called Hranice: the district picks Bandzone's right one
                new Location("Garáž u Nováků", "Hranice", Country.CZECHIA, new Address(null, "753 01",
                        "okres Přerov", "Olomoucký kraj", 49.548, 17.735)), editedLineup,
                Admission.voluntary(), "edited by the live test", null, null, null, false);

        assertThatCode(() -> {
            try (BandzoneSession session = client.openSession()) {
                String id = session.createGig(gig);
                assertThat(id).matches("\\d+");
                try {
                    session.updateGig(id, gig);    // what publishing does after the wizard
                    session.updateGig(id, edited); // each throws if Bandzone did not store it
                } finally {
                    if (keep) {
                        System.out.println("BZ_KEEP: kept concert " + id);
                    } else {
                        session.deleteGig(id);
                    }
                }
            }
        }).doesNotThrowAnyException();
    }
}
