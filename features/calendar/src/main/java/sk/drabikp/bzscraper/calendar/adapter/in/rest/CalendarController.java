package sk.drabikp.bzscraper.calendar.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.AddToCatalogJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarApi;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarOverviewJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.LinkJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.LinkedJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.VerdictJson;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarCatalogUseCase;
import sk.drabikp.bzscraper.calendar.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.gig.api.GigDraftMapping;
import sk.drabikp.bzscraper.gig.application.InvalidGigException;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.time.Clock;
import java.util.List;

/**
 * Serves the band calendar (calendar.yaml). The calendar itself is never changed; the use cases
 * announce their changes on the live updates themselves.
 */
@RestController
class CalendarController implements CalendarApi {

    private final ReviewCalendarUseCase review;
    private final CalendarCatalogUseCase catalog;
    private final Clock clock;

    CalendarController(ReviewCalendarUseCase review, CalendarCatalogUseCase catalog, Clock clock) {
        this.review = review;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<CalendarOverviewJson> calendarOverview() {
        return ResponseEntity.ok(review.configured() ? CalendarMapping.toJson(review.overview(), clock)
                : CalendarMapping.unconfigured());
    }

    @Override
    public ResponseEntity<CalendarOverviewJson> readCalendar() {
        return ResponseEntity.ok(CalendarMapping.toJson(review.read(), clock));
    }

    @Override
    public ResponseEntity<Void> seenAll() {
        review.seenAll();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<LinkedJson> linkSameDay() {
        return ResponseEntity.ok(new LinkedJson(catalog.linkSameDayGigs()));
    }

    @Override
    public ResponseEntity<List<RuleJson>> calendarRules() {
        return ResponseEntity.ok(CalendarMapping.toJson(review.rules()));
    }

    @Override
    public ResponseEntity<Void> decideEvent(String eventId, VerdictJson verdict) {
        review.decide(eventId, CalendarMapping.toKind(verdict.getKind()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> forgetVerdict(String eventId) {
        review.forget(eventId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> seenEvent(String eventId) {
        review.seen(eventId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> addEventToCatalog(String eventId, AddToCatalogJson request) {
        catalog.addToCatalog(eventId, InvalidGigException.gigOf(GigDraftMapping.toDraft(request.getGig())));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> linkEvent(String eventId, LinkJson link) {
        catalog.link(eventId, GigId.fromToken(link.getGig()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unlinkEvent(String eventId) {
        catalog.unlink(eventId);
        return ResponseEntity.noContent().build();
    }
}
