package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.domain.model.BandProfile;
import sk.drabikp.bzscraper.domain.model.CalendarChange;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.CalendarEventStatus;
import sk.drabikp.bzscraper.domain.model.CalendarOverview;
import sk.drabikp.bzscraper.domain.model.CalendarRow;
import sk.drabikp.bzscraper.domain.model.CatalogMatch;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.model.RuleKind;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarReviewServiceTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final LocalDateTime FEST_DAY = LocalDateTime.of(2026, 11, 20, 16, 0);

    private final CalendarFeed feed = mock(CalendarFeed.class);
    private final BandProfileStore profiles = mock(BandProfileStore.class);
    private final CalendarFakes.Decisions decisions = new CalendarFakes.Decisions();
    private final CalendarFakes.Snapshots snapshots = new CalendarFakes.Snapshots();
    private final CalendarFakes.Links links = new CalendarFakes.Links();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final CalendarReviewService service = new CalendarReviewService(feed, profiles, decisions, snapshots,
            links, gigs, new SyncFakes.DirectTransactions(), clock, new BandProfile.Thresholds(4, -1, -4));

    @BeforeEach
    void rules() {
        when(profiles.rules()).thenReturn(List.of(
                ProfileRule.preset(RuleKind.TITLE_CONTAINS, "fest", 3),
                ProfileRule.preset(RuleKind.NOTES_LABEL, "showtime", 5),
                ProfileRule.preset(RuleKind.CATALOG_GIG_SAME_DAY, null, 2),
                ProfileRule.preset(RuleKind.CANCELLED_TITLE, "zrusene", 0),
                ProfileRule.preset(RuleKind.SHOWTIME_LABEL, "showtime", 0)));
    }

    private static CalendarEvent event(String id, String title, LocalDateTime start, String notes) {
        return new CalendarEvent(id, title, "Klub 007, 160 00 Praha 6, Česko", notes, start, start.plusHours(6),
                false, false, false, CalendarEventStatus.CONFIRMED, null);
    }

    private static CalendarEvent fest() {
        return event("fest", "Fest", FEST_DAY, "Showtime: 20:30");
    }

    private CalendarOverview read(CalendarEvent... events) throws Exception {
        when(feed.read()).thenReturn(List.of(events));
        clock.advance(Duration.ofMinutes(10));
        return service.read();
    }

    private static CalendarRow row(CalendarOverview overview, String id) {
        return overview.rows().stream().filter(r -> r.eventId().equals(id)).findFirst().orElseThrow();
    }

    private static Gig festGig() {
        return TestGigs.gig("Fest", "Klub 007", ZonedDateTime.of(FEST_DAY.toLocalDate().atTime(20, 30), PRAGUE));
    }

    @Test
    void classifies_with_the_stored_rules_the_catalogs_gig_days_and_the_users_verdicts() throws Exception {
        gigs.save(festGig());
        decisions.decide("visit", CalendarEventKind.NOT_GIG);

        CalendarOverview overview = read(event("fest", "Fest", FEST_DAY, ""), event("visit", "Fest", FEST_DAY.plusDays(1), ""));

        assertThat(row(overview, "fest").classification().kind()).isEqualTo(CalendarEventKind.GIG); // +3 fest, +2 catalog
        assertThat(row(overview, "visit").classification().kind()).isEqualTo(CalendarEventKind.NOT_GIG);
        assertThat(row(overview, "visit").classification().decidedByUser()).isTrue();
        assertThat(overview.lastRead()).isEqualTo(clock.instant());
    }

    @Test
    void the_first_read_is_the_starting_point_and_later_reads_show_what_is_new_changed_or_gone() throws Exception {
        CalendarEvent rehearsal = event("reh", "Rehearsal", FEST_DAY.minusDays(3), "");
        CalendarOverview first = read(event("fest", "Fest", FEST_DAY, ""), rehearsal);
        assertThat(first.rows()).allSatisfy(r -> assertThat(r.change()).isNull());

        CalendarEvent club = event("club", "Club night", FEST_DAY.plusDays(7), "");
        CalendarOverview second = read(fest(), club);

        assertThat(row(second, "club").change().type()).isEqualTo(CalendarChange.Type.NEW);
        CalendarChange festChange = row(second, "fest").change();
        assertThat(festChange.type()).isEqualTo(CalendarChange.Type.CHANGED);
        assertThat(festChange.fields()).containsExactly(CalendarChange.Field.NOTES);
        assertThat(festChange.suggestedBefore()).as("the new notes made it a sure gig").isEqualTo(CalendarEventKind.UNSURE);
        assertThat(row(second, "reh").removed()).isTrue();
        assertThat(row(second, "reh").change().type()).isEqualTo(CalendarChange.Type.REMOVED);

        CalendarOverview third = read(fest(), club);
        assertThat(row(third, "fest").change()).as("unseen changes stay").isNotNull();

        service.seenAll();

        assertThat(service.overview().rows()).extracting(CalendarRow::eventId).containsExactlyInAnyOrder("fest", "club");
        assertThat(service.overview().rows()).allSatisfy(r -> assertThat(r.change()).isNull());
    }

    @Test
    void a_gig_is_added_to_the_catalog_from_the_calendar_and_stays_linked_to_its_event() throws Exception {
        read(fest());
        CalendarRow before = row(service.overview(), "fest");
        assertThat(before.missingFromCatalog()).isTrue();
        assertThat(before.draft().showTime()).hasToString("20:30");

        service.addToCatalog("fest", festGig());

        CalendarRow after = row(service.overview(), "fest");
        assertThat(gigs.findById(festGig().id())).isPresent();
        assertThat(after.match().state()).isEqualTo(CatalogMatch.State.LINKED);
        assertThat(after.match().differences()).isEmpty();
        assertThatThrownBy(() -> service.addToCatalog("other", festGig()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("link the event");
    }

    @Test
    void a_linked_gig_shows_what_the_calendar_now_says_differently() throws Exception {
        read(fest(), event("club", "Club fest", FEST_DAY.plusDays(7), "Showtime: 21:00"));
        service.addToCatalog("fest", festGig());
        Gig club = TestGigs.gig("Club fest", "Klub 007",
                ZonedDateTime.of(FEST_DAY.plusDays(7).toLocalDate().atTime(21, 0), PRAGUE));
        service.addToCatalog("club", club);

        CalendarOverview overview = read(
                event("fest", "ZRUŠENÉ Fest", FEST_DAY.plusDays(1), "Showtime: 20:30"));

        assertThat(row(overview, "fest").match().differences()).extracting(CatalogMatch.Difference::kind)
                .containsExactly(CatalogMatch.Kind.CANCELLED, CatalogMatch.Kind.DATE);
        assertThat(row(overview, "club").match().differences()).extracting(CatalogMatch.Difference::kind)
                .containsExactly(CatalogMatch.Kind.REMOVED);

        service.seen("club");

        assertThat(service.overview().rows()).extracting(CalendarRow::eventId).containsExactly("fest");
        assertThat(links.all()).doesNotContainKey("club");
    }

    @Test
    void gig_events_are_linked_to_the_one_catalog_gig_on_their_day() throws Exception {
        gigs.save(festGig());
        Gig twin1 = TestGigs.gig("A", "Klub A", ZonedDateTime.of(FEST_DAY.plusDays(1), PRAGUE));
        Gig twin2 = TestGigs.gig("B", "Klub B", ZonedDateTime.of(FEST_DAY.plusDays(1), PRAGUE));
        gigs.save(twin1);
        gigs.save(twin2);
        read(fest(), event("double", "Double fest", FEST_DAY.plusDays(1), "Showtime: 20:00"));

        assertThat(service.linkSameDayGigs()).isEqualTo(1);

        assertThat(links.all()).containsOnlyKeys("fest");
        assertThat(row(service.overview(), "double").match().sameDay()).hasSize(2);
    }

    @Test
    void a_verdict_is_gig_or_not_a_gig() {
        service.decide("uid", CalendarEventKind.GIG);
        service.forget("other");

        assertThat(decisions.all()).containsOnlyKeys("uid");
        assertThatThrownBy(() -> service.decide("uid", CalendarEventKind.UNSURE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
