package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange;
import sk.drabikp.bzscraper.calendar.domain.KnownCalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JpaCalendarStoresTest {

    @Autowired
    private JpaBandProfileStore profiles;

    @Autowired
    private JpaCalendarDecisionStore decisions;

    @Autowired
    private JpaCalendarSnapshotStore snapshots;

    @Autowired
    private JpaCalendarLinkStore links;

    private static ProfileRule user(RuleKind kind, String value, int weight) {
        return new ProfileRule(kind, value, weight, RuleOrigin.USER, true);
    }

    @Test
    void replacing_one_origin_keeps_the_others_and_the_order() {
        List<ProfileRule> presets = profiles.rules().stream().filter(r -> r.origin() == RuleOrigin.PRESET).toList();

        profiles.replace(RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "jana", -4), user(RuleKind.REPEATING, null, -8)));
        profiles.replace(RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "jana|janka", -4),
                new ProfileRule(RuleKind.TITLE_CONTAINS, "fest", 2, RuleOrigin.USER, false)));

        assertThat(profiles.rules()).filteredOn(r -> r.origin() == RuleOrigin.USER).containsExactly(
                user(RuleKind.MEMBER, "jana|janka", -4),
                new ProfileRule(RuleKind.TITLE_CONTAINS, "fest", 2, RuleOrigin.USER, false));
        assertThat(profiles.rules()).filteredOn(r -> r.origin() == RuleOrigin.PRESET).isEqualTo(presets);
    }

    @Test
    void rules_of_another_origin_are_refused() {
        assertThatThrownBy(() -> profiles.replace(RuleOrigin.LEARNED, List.of(user(RuleKind.MEMBER, "jana", -4))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remembers_changes_and_forgets_verdicts() {
        decisions.decide("uid-1", CalendarEventKind.GIG);
        decisions.decide("uid-2", CalendarEventKind.NOT_GIG);
        decisions.decide("uid-1", CalendarEventKind.NOT_GIG);
        decisions.forget("uid-2");
        decisions.forget("never-decided");

        assertThat(decisions.all()).containsExactly(Map.entry("uid-1", CalendarEventKind.NOT_GIG));
    }

    @Test
    void the_saved_calendar_keeps_every_field_and_the_unseen_change() {
        Instant firstRead = Instant.parse("2026-10-01T08:00:00Z");
        Instant secondRead = Instant.parse("2026-10-08T08:00:00Z");
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 16, 0);
        CalendarEvent event = new CalendarEvent("uid-1", "Fest", "Klub, Praha", "Showtime: 20:30\nCena: 500",
                start, start.plusHours(8), false, false, true, CalendarEventStatus.TENTATIVE, firstRead);
        CalendarChange change = new CalendarChange(CalendarChange.Type.CHANGED,
                Set.of(CalendarChange.Field.NOTES, CalendarChange.Field.TIME), CalendarEventKind.UNSURE, secondRead);
        KnownCalendarEvent known = new KnownCalendarEvent(event, CalendarEventKind.GIG, firstRead, null, change);

        snapshots.saveRead(List.of(known), secondRead);

        assertThat(snapshots.find("uid-1")).contains(known);
        assertThat(snapshots.lastRead()).contains(secondRead);

        snapshots.save(known.seen());
        assertThat(snapshots.all().get("uid-1").change()).isNull();
        assertThat(snapshots.lastRead()).as("marking seen is not a read").contains(secondRead);

        snapshots.remove("uid-1");
        assertThat(snapshots.all()).isEmpty();
    }

    @Test
    void links_follow_a_gig_whose_identity_changed() {
        GigId gig = new GigId(LocalDate.of(2026, 11, 20), "klub");
        GigId moved = new GigId(LocalDate.of(2026, 11, 21), "klub");
        links.link("uid-1", gig);
        links.link("uid-2", new GigId(LocalDate.of(2026, 12, 1), "@brno"));

        links.move(gig, moved);
        links.unlink("uid-2");

        assertThat(links.all()).containsExactly(Map.entry("uid-1", moved));
    }
}
