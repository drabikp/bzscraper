package sk.drabikp.bzscraper.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarFilterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static CalendarRow row(LocalDate day, CalendarEventKind kind, CalendarEventStatus status,
                                   CatalogMatch.State state, CalendarChange change, boolean removed) {
        CalendarEvent event = new CalendarEvent("e-" + day + kind + status + state + removed, "Event", "", "",
                day.atTime(18, 0), null, false, false, false, status, null);
        CalendarClassification classification = new CalendarClassification(event, kind, kind, false, status, 0,
                List.of());
        CalendarGigDraft draft = new CalendarGigDraft(event.id(), "Event", day, null, null, "Praha", Country.CZECHIA,
                null, null, null);
        return new CalendarRow(classification, draft, new CatalogMatch(state, null, List.of(), List.of()), change,
                removed);
    }

    private static CalendarRow gig(LocalDate day, CatalogMatch.State state) {
        return row(day, CalendarEventKind.GIG, CalendarEventStatus.CONFIRMED, state, null, false);
    }

    @Test
    void needs_a_look_shows_recent_and_upcoming_gigs_missing_from_the_catalog_but_not_old_or_cancelled_ones() {
        CalendarRow upcoming = gig(TODAY.plusDays(10), CatalogMatch.State.MISSING);
        CalendarRow recent = gig(TODAY.minusDays(CalendarFilter.RECENT_DAYS), CatalogMatch.State.MISSING);
        CalendarRow old = gig(TODAY.minusDays(CalendarFilter.RECENT_DAYS + 1), CatalogMatch.State.MISSING);
        CalendarRow linked = gig(TODAY.plusDays(10), CatalogMatch.State.LINKED);
        CalendarRow cancelled = row(TODAY.plusDays(3), CalendarEventKind.GIG, CalendarEventStatus.CANCELLED,
                CatalogMatch.State.MISSING, null, false);

        assertThat(List.of(upcoming, recent, old, linked, cancelled).stream()
                .filter(CalendarFilter.NEEDS_A_LOOK.on(TODAY))).containsExactly(upcoming, recent);
        assertThat(List.of(upcoming, old, linked, cancelled).stream().filter(CalendarFilter.MISSING.on(TODAY)))
                .containsExactly(upcoming, old, cancelled);
    }

    @Test
    void needs_a_look_shows_unseen_changes_any_time_and_unsure_events_only_ahead() {
        CalendarRow changedLongAgo = row(TODAY.minusYears(1), CalendarEventKind.NOT_GIG, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, new CalendarChange(CalendarChange.Type.CHANGED,
                        Set.of(CalendarChange.Field.TIME), null, Instant.parse("2026-10-01T10:00:00Z")), false);
        CalendarRow unsureAhead = row(TODAY, CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, null, false);
        CalendarRow unsurePast = row(TODAY.minusDays(1), CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, null, false);
        CalendarRow unsureRemoved = row(TODAY.plusDays(1), CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED,
                CatalogMatch.State.MISSING, null, true);

        assertThat(List.of(changedLongAgo, unsureAhead, unsurePast, unsureRemoved).stream()
                .filter(CalendarFilter.NEEDS_A_LOOK.on(TODAY))).containsExactly(changedLongAgo, unsureAhead);
        assertThat(List.of(unsureAhead, unsurePast, unsureRemoved).stream().filter(CalendarFilter.NOT_SURE.on(TODAY)))
                .containsExactly(unsureAhead, unsurePast);
    }

    @Test
    void the_counts_leave_removed_events_out_and_count_only_recent_missing_gigs() {
        List<CalendarRow> rows = List.of(
                gig(TODAY.plusDays(10), CatalogMatch.State.MISSING),
                gig(TODAY.minusYears(1), CatalogMatch.State.MISSING),
                gig(TODAY.plusDays(20), CatalogMatch.State.LINKED),
                row(TODAY, CalendarEventKind.UNSURE, CalendarEventStatus.CONFIRMED, CatalogMatch.State.MISSING, null,
                        false),
                row(TODAY, CalendarEventKind.GIG, CalendarEventStatus.CONFIRMED, CatalogMatch.State.MISSING, null,
                        true));

        CalendarFilter.Counts counts = CalendarFilter.count(rows, TODAY);

        assertThat(counts.events()).isEqualTo(4);
        assertThat(counts.gigs()).isEqualTo(3);
        assertThat(counts.notSure()).isEqualTo(1);
        assertThat(counts.missing()).isEqualTo(1);
        assertThat(counts.linkable()).isZero();
    }
}
