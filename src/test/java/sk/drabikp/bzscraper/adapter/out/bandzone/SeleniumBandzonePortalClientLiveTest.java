package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Live integration test — drives real Bandzone with a real browser and CREATES a
 * gig on the configured band. Skipped unless {@code BZ_LIVE=true}. Reads creds from
 * env so nothing is committed:
 *   BZ_LOGIN, BZ_PASSWORD, BZ_SLUG  (required)
 *   BZ_CHROMIUM (default /usr/bin/chromium), BZ_CHROMEDRIVER (default /usr/bin/chromedriver)
 *
 * The created gig is left on the band — delete it manually afterwards.
 */
@EnabledIfEnvironmentVariable(named = "BZ_LIVE", matches = "true")
class SeleniumBandzonePortalClientLiveTest {

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    @Test
    void creates_a_gig_on_the_real_band_without_error() {
        SeleniumBandzonePortalClient client = new SeleniumBandzonePortalClient(
                env("BZ_BASE_URL", "https://bandzone.cz"),
                env("BZ_LOGIN", ""),
                env("BZ_PASSWORD", ""),
                env("BZ_SLUG", ""),
                Boolean.parseBoolean(env("BZ_HEADLESS", "true")),
                env("BZ_CHROMIUM", "/usr/bin/chromium"),
                env("BZ_CHROMEDRIVER", "/usr/bin/chromedriver"));

        Gig gig = Gig.create("TEST selenium client (smazat)",
                GigSchedule.startingAt(ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, ZoneId.of("Europe/Prague"))),
                new Location("Klub 007", "Praha", Country.CZECHIA), List.of(), Admission.free(),
                null, null, null, null);

        assertThatCode(() -> {
            try (BandzoneSession session = client.openSession()) {
                session.createGig(gig);
            }
        }).doesNotThrowAnyException();
    }
}
