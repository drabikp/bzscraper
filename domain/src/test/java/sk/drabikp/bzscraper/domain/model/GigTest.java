package sk.drabikp.bzscraper.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GigTest {

    private static final GigSchedule SCHEDULE =
            GigSchedule.startingAt(ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));
    private static final Location LOCATION = new Location("Klub 007", "Praha", Country.CZECHIA);

    private static Gig valid(List<String> lineup) {
        return Gig.create("Show", SCHEDULE, LOCATION, lineup, Admission.free(), null, null, null, null);
    }

    @Test
    void a_created_gig_is_valid_and_not_cancelled() {
        Gig gig = valid(List.of("Eufory"));
        assertThat(gig.cancelled()).isFalse();
        assertThat(gig.title()).isEqualTo("Show");
    }

    @Test
    void title_schedule_location_and_admission_are_required() {
        assertThatThrownBy(() -> Gig.create(null, SCHEDULE, LOCATION, List.of(), Admission.free(), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gig.create("  ", SCHEDULE, LOCATION, List.of(), Admission.free(), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gig.create("Show", null, LOCATION, List.of(), Admission.free(), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gig.create("Show", SCHEDULE, null, List.of(), Admission.free(), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gig.create("Show", SCHEDULE, LOCATION, List.of(), null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineup_null_becomes_empty_and_is_defensively_copied() {
        assertThat(valid(null).lineup()).isEmpty();

        List<String> source = new ArrayList<>(List.of("Eufory"));
        Gig gig = valid(source);
        source.add("Mutated");
        assertThat(gig.lineup()).containsExactly("Eufory");
    }

    @Test
    void identity_is_start_date_plus_normalized_venue() {
        GigId id = valid(List.of()).id();
        assertThat(id.date()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(id.venue()).isEqualTo("klub 007");
    }

    @Test
    void rescheduling_returns_a_new_gig_with_the_new_schedule() {
        Gig gig = valid(List.of());
        GigSchedule later =
                GigSchedule.startingAt(ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

        Gig moved = gig.rescheduledTo(later);

        assertThat(moved.schedule()).isEqualTo(later);
        assertThat(moved.title()).isEqualTo(gig.title());
        assertThat(moved.id().date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(gig.schedule()).isEqualTo(SCHEDULE); // original unchanged
    }

    @Test
    void cancelling_returns_a_cancelled_copy_and_reactivating_clears_it() {
        Gig gig = valid(List.of());
        Gig cancelled = gig.cancel();
        assertThat(cancelled.cancelled()).isTrue();
        assertThat(gig.cancelled()).isFalse();
        assertThat(cancelled.reactivate().cancelled()).isFalse();
    }

    @Test
    void a_gig_without_a_venue_is_identified_by_its_city() {
        java.time.ZonedDateTime start = java.time.ZonedDateTime.of(2024, 6, 15, 20, 0, 0, 0,
                java.time.ZoneId.of("Europe/Prague"));
        Gig petrvald = Gig.create("Eufory", GigSchedule.startingAt(start),
                new Location(null, "Petřvald", Country.CZECHIA), java.util.List.of(), Admission.free(),
                null, null, null, null);
        Gig presov = Gig.create("Dobrý festival", GigSchedule.startingAt(start),
                new Location("TBA ", "Prešov", Country.SLOVAKIA), java.util.List.of(), Admission.free(),
                null, null, null, null);

        org.assertj.core.api.Assertions.assertThat(petrvald.id().venue()).isEqualTo("@petřvald");
        org.assertj.core.api.Assertions.assertThat(petrvald.id()).isNotEqualTo(presov.id());
    }
}
