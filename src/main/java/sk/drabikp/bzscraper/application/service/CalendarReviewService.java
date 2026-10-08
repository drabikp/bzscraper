package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.domain.model.BandProfile;
import sk.drabikp.bzscraper.domain.model.CalendarClassification;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.service.CalendarEventClassifier;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calendar orchestrator: reads the calendar, classifies its events with the stored band
 * profile ({@link CalendarEventClassifier}), using the catalog's gig days as evidence, and
 * keeps the user's verdicts. Read-only towards the calendar and the catalog.
 */
public class CalendarReviewService implements ReviewCalendarUseCase {

    private final CalendarFeed feed;
    private final BandProfileStore profileStore;
    private final CalendarDecisionStore decisionStore;
    private final GigRepository gigRepository;
    private final BandProfile.Thresholds thresholds;

    public CalendarReviewService(CalendarFeed feed, BandProfileStore profileStore, CalendarDecisionStore decisionStore,
                                 GigRepository gigRepository, BandProfile.Thresholds thresholds) {
        this.feed = feed;
        this.profileStore = profileStore;
        this.decisionStore = decisionStore;
        this.gigRepository = gigRepository;
        this.thresholds = thresholds;
    }

    @Override
    public boolean configured() {
        return feed.configured();
    }

    @Override
    public List<CalendarEvent> read() throws CalendarUnavailableException {
        return feed.read();
    }

    @Override
    public List<CalendarClassification> classify(List<CalendarEvent> events) {
        Set<LocalDate> catalogDays = gigRepository.findAll().stream()
                .map(gig -> gig.schedule().startDate())
                .collect(Collectors.toSet());
        return CalendarEventClassifier.classify(events, new BandProfile(profileStore.rules(), thresholds),
                catalogDays, decisionStore.all());
    }

    @Override
    public void decide(String eventId, CalendarEventKind verdict) {
        if (verdict == CalendarEventKind.UNSURE) {
            throw new IllegalArgumentException("a verdict is gig or not a gig");
        }
        decisionStore.decide(eventId, verdict);
    }

    @Override
    public void forget(String eventId) {
        decisionStore.forget(eventId);
    }

    @Override
    public List<ProfileRule> rules() {
        return profileStore.rules();
    }
}
