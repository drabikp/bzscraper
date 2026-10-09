package sk.drabikp.bzscraper.calendar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.calendar.application.CalendarLinksFollowGigs;
import sk.drabikp.bzscraper.calendar.application.CalendarReviewService;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.calendar.domain.rules.BandProfile;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;

import java.time.Clock;

@Configuration
class CalendarConfiguration {

    @Bean
    CalendarReviewService calendarReviewService(
            CalendarFeed feed, BandProfileStore profileStore, CalendarDecisionStore decisionStore,
            CalendarSnapshotStore snapshotStore, CalendarLinkStore linkStore, GigRepository gigRepository,
            Transactions transactions, Clock clock, GigWrites catalogWrites, CalendarProperties calendar,
            LiveUpdates live) {
        return new CalendarReviewService(feed, profileStore, decisionStore, snapshotStore, linkStore, gigRepository,
                transactions, clock, new BandProfile.Thresholds(calendar.gigScore(), calendar.notGigScore(),
                        calendar.strongNegative()), catalogWrites, live);
    }

    /** A calendar event's link follows its gig when an edit moves the gig's identity. */
    @Bean
    CalendarLinksFollowGigs calendarLinksFollowGigs(CalendarLinkStore linkStore) {
        return new CalendarLinksFollowGigs(linkStore);
    }
}
