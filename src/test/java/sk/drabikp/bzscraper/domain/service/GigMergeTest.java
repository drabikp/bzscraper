package sk.drabikp.bzscraper.domain.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.ImportProposal.Version;
import sk.drabikp.bzscraper.domain.model.Location;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigMergeTest {

    private static final GigSchedule SCHEDULE = GigSchedule.startingAt(
            ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

    private final Gig bandzone = new Gig("Fest", SCHEDULE, new Location("Lucerna Music Bar", "Praha", Country.CZECHIA),
            List.of("Support"), Admission.paid("200 Kč"), null, "https://fb.example/e", null,
            "https://img.example/p.jpg", false);
    private final Gig bandsintown = new Gig("Fest 2026", SCHEDULE, new Location("Lucerna", "Praha", Country.CZECHIA),
            List.of(), Admission.free(), "From Bandsintown", null, null, null, false);
    private final Version bz = new Version(Platform.BANDZONE, bandzone);
    private final Version bit = new Version(Platform.BANDSINTOWN, bandsintown);

    @Test
    void the_chosen_version_decides_what_the_gig_is_and_the_others_fill_the_gaps() {
        Gig merged = GigMerge.merge(bit, List.of(bz, bit));

        assertThat(merged.title()).isEqualTo("Fest 2026");
        assertThat(merged.location().venue()).isEqualTo("Lucerna");
        assertThat(merged.description()).isEqualTo("From Bandsintown");
        assertThat(merged.lineup()).containsExactly("Support");
        assertThat(merged.facebookUrl()).isEqualTo("https://fb.example/e");
        assertThat(merged.posterImageUrl()).isEqualTo("https://img.example/p.jpg");
    }

    @Test
    void entry_never_comes_from_bandsintown_when_another_version_knows_it() {
        assertThat(GigMerge.merge(bit, List.of(bz, bit)).admission()).isEqualTo(Admission.paid("200 Kč"));
        assertThat(GigMerge.merge(bit, List.of(bit)).admission()).isEqualTo(Admission.free());
    }

    @Test
    void an_end_time_known_only_to_another_version_is_kept() {
        Gig withEnd = new Gig("Fest", new GigSchedule(SCHEDULE.start(), SCHEDULE.start().plusHours(6)),
                bandsintown.location(), List.of(), Admission.free(), null, null, null, null, false);

        Gig merged = GigMerge.merge(bz, List.of(bz, new Version(Platform.BANDSINTOWN, withEnd)));

        assertThat(merged.schedule().end()).isEqualTo(SCHEDULE.start().plusHours(6));
    }

    @Test
    void a_gig_cancelled_in_any_version_stays_cancelled() {
        Version cancelledOnBandzone = new Version(Platform.BANDZONE, bandzone.cancel());

        assertThat(GigMerge.merge(bit, List.of(cancelledOnBandzone, bit)).cancelled()).isTrue();
    }
}
