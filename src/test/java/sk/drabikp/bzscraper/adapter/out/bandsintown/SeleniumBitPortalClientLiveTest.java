package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitSession.Created;
import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Live integration test — drives the real Bandsintown artist portal: CREATES a made-up
 * event (published WITHOUT notifying followers), EDITS it, then DELETES it as cancelled
 * (cleanup runs even when a step fails), and checks that deleting it again is a no-op.
 * Skipped unless {@code BIT_LIVE=true}. Reads creds from env so nothing is committed:
 *   BIT_LOGIN, BIT_PASSWORD, BIT_TOTP (authenticator secret)  (required)
 *   BIT_CLEANUP_ID  only delete this event id (e.g. one a failed run left behind)
 *   BIT_INSPECT_IDS only print how the portal lists these event ids (comma-separated) — read-only
 *   BIT_HEADLESS (default true), BIT_CHROMIUM (default /usr/bin/chromium),
 *   BIT_CHROMEDRIVER (default /usr/bin/chromedriver), BIT_PROFILE (browser profile dir)
 *
 * The event is public for about a minute on the account's artist page.
 */
@EnabledIfEnvironmentVariable(named = "BIT_LIVE", matches = "true")
class SeleniumBitPortalClientLiveTest {

    private static final ZoneId BRATISLAVA = ZoneId.of("Europe/Bratislava");

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    @Test
    void creates_publishes_silently_edits_and_deletes_an_event() {
        SeleniumBitPortalClient client = new SeleniumBitPortalClient(
                "https://artists.bandsintown.com",
                env("BIT_LOGIN", ""),
                env("BIT_PASSWORD", ""),
                env("BIT_TOTP", ""),
                "",
                env("BIT_ARTIST_NAME", "Eufory (Band)"),
                Boolean.parseBoolean(env("BIT_HEADLESS", "true")),
                env("BIT_CHROMIUM", "/usr/bin/chromium"),
                env("BIT_CHROMEDRIVER", "/usr/bin/chromedriver"),
                env("BIT_PROFILE", ""),
                env("BIT_PASSWORD_STORE", "auto"),
                600, 1500);

        Gig gig = new Gig("BZSCRAPER TEST - please ignore",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 7, 20, 0, 0, 0, BRATISLAVA)),
                new Location("BZSCRAPER TEST VENUE", "Bratislava", Country.SLOVAKIA), List.of(),
                Admission.free(), "Automated test event - will be deleted.", null, null, null, false);
        Gig edited = new Gig("BZSCRAPER TEST - please ignore",
                GigSchedule.startingAt(ZonedDateTime.of(2027, 10, 7, 21, 30, 0, 0, BRATISLAVA)),
                new Location("BZSCRAPER TEST VENUE", "Bratislava", Country.SLOVAKIA), List.of(),
                Admission.free(), "Automated test event - edited, will be deleted.", null, null, null, false);

        String inspectIds = env("BIT_INSPECT_IDS", "");
        if (!inspectIds.isBlank()) {
            assertThatCode(() -> {
                try (BitSession session = client.openSession()) {
                    List<String> ids = List.of(inspectIds.split(","));
                    Map<String, String> found = ((SeleniumBitSession) session).inspect(ids);
                    ids.forEach(id -> System.out.println("BIT inspect: " + id + " -> "
                            + found.getOrDefault(id, "not listed (upcoming or past)")));
                }
            }).doesNotThrowAnyException();
            return;
        }

        String cleanupId = env("BIT_CLEANUP_ID", "");
        if (!cleanupId.isBlank()) {
            assertThatCode(() -> {
                try (BitSession session = client.openSession()) {
                    session.deleteEvent(cleanupId, false);
                }
            }).doesNotThrowAnyException();
            return;
        }

        assertThatCode(() -> {
            try (BitSession session = client.openSession()) {
                List<Created> created = session.createEvents(List.of(gig), false);
                assertThat(created).singleElement().satisfies(c ->
                        assertThat(c.error()).as("create error").isNull());
                String id = created.getFirst().eventId();
                try {
                    session.updateEvent(id, edited);
                } finally {
                    session.deleteEvent(id, true);
                    session.deleteEvent(id, true);      // already gone: no-op
                    System.out.println("BIT live test: created, edited and deleted event " + id);
                }
            }
        }).doesNotThrowAnyException();
    }
}
