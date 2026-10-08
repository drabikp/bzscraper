package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.domain.model.BandProfile;
import sk.drabikp.bzscraper.domain.model.CalendarClassification;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.CalendarEventStatus;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.model.RuleKind;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CalendarReviewServiceTest {

    private final CalendarFeed feed = mock(CalendarFeed.class);
    private final BandProfileStore profiles = mock(BandProfileStore.class);
    private final CalendarDecisionStore decisions = mock(CalendarDecisionStore.class);
    private final GigRepository gigs = mock(GigRepository.class);
    private final CalendarReviewService service = new CalendarReviewService(feed, profiles, decisions, gigs,
            new BandProfile.Thresholds(4, -1, -4));

    private static CalendarEvent event(String id, String title, LocalDateTime start) {
        return new CalendarEvent(id, title, "", "", start, start.plusHours(2), false, false, false,
                CalendarEventStatus.CONFIRMED, null);
    }

    @Test
    void classifies_with_the_stored_rules_the_catalogs_gig_days_and_the_users_verdicts() {
        LocalDateTime gigDay = LocalDateTime.of(2026, 9, 15, 18, 0);
        when(profiles.rules()).thenReturn(List.of(
                ProfileRule.preset(RuleKind.TITLE_CONTAINS, "fest", 3),
                ProfileRule.preset(RuleKind.CATALOG_GIG_SAME_DAY, null, 2)));
        when(gigs.findAll()).thenReturn(List.of(TestGigs.gig("Fest", "Klub",
                ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")))));
        when(decisions.all()).thenReturn(Map.of("visit", CalendarEventKind.NOT_GIG));

        List<CalendarClassification> results = service.classify(List.of(
                event("gig", "Fest", gigDay), event("visit", "Fest", gigDay.plusDays(1))));

        assertThat(results.get(0).kind()).isEqualTo(CalendarEventKind.GIG);         // +3 fest, +2 catalog
        assertThat(results.get(1).kind()).isEqualTo(CalendarEventKind.NOT_GIG);
        assertThat(results.get(1).decidedByUser()).isTrue();
    }

    @Test
    void a_verdict_is_gig_or_not_a_gig() {
        service.decide("uid", CalendarEventKind.GIG);
        service.forget("other");

        verify(decisions).decide("uid", CalendarEventKind.GIG);
        verify(decisions).forget("other");
        assertThatThrownBy(() -> service.decide("uid", CalendarEventKind.UNSURE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(feed);
    }
}
