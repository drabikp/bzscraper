package sk.drabikp.bzscraper.calendar.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarCatalogUseCase;
import sk.drabikp.bzscraper.calendar.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange;
import sk.drabikp.bzscraper.calendar.domain.CalendarFilter;
import sk.drabikp.bzscraper.calendar.domain.CalendarGigDraft;
import sk.drabikp.bzscraper.calendar.domain.CalendarOverview;
import sk.drabikp.bzscraper.calendar.domain.CalendarRow;
import sk.drabikp.bzscraper.calendar.domain.CatalogMatch;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification;
import sk.drabikp.bzscraper.gig.application.InvalidGigException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigDraft;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The band calendar over HTTP: its events sorted into gigs and the rest and checked against
 * the catalog, reading it again, the user's verdicts, and putting an event into the catalog or
 * linking it to a gig there. The calendar itself is never changed; a linked gig the calendar
 * moved or cancelled is changed through the catalog's own API (so the platforms follow).
 */
@RestController
@RequestMapping("/api/calendar")
class CalendarEndpoint {

    private final ReviewCalendarUseCase review;
    private final CalendarCatalogUseCase catalog;
    private final Clock clock;
    private final LiveUpdates live;

    CalendarEndpoint(ReviewCalendarUseCase review, CalendarCatalogUseCase catalog, Clock clock, LiveUpdates live) {
        this.review = review;
        this.catalog = catalog;
        this.clock = clock;
        this.live = live;
    }

    /** {@code filters}: the views this row is in ({@link CalendarFilter}). */
    record OverviewJson(boolean configured, String lastRead, CalendarFilter.Counts counts, List<RowJson> rows) {
    }

    record RowJson(String eventId, String title, String location, String notes, String start, String end,
                   boolean allDay, String kind, String suggested, boolean decidedByUser, String status, int score,
                   List<ReasonJson> reasons, DraftJson draft, MatchJson match, ChangeJson change, boolean removed,
                   boolean missingFromCatalog, boolean needsAttention, List<String> filters) {
    }

    record ReasonJson(String why, String value, int weight, String text) {
    }

    /** What the event says of a gig, to pre-fill the gig form. */
    record DraftJson(String title, LocalDate date, String showTime, String eventStart, String venue, String city,
                     Country country, String street, String postalCode) {
    }

    record MatchJson(String state, GigRef gig, List<GigRef> sameDay, List<CatalogMatch.Difference> differences) {
    }

    record GigRef(String id, String title, String start, String city, boolean cancelled) {
    }

    record ChangeJson(String type, List<String> fields, String suggestedBefore, String at) {
    }

    record Verdict(CalendarEventKind kind) {
    }

    record AddToCatalog(GigDraft gig) {
    }

    record Link(String gig) {
    }

    record Linked(int linked) {
    }

    record RuleJson(String kind, String value, int weight, boolean weighted, String origin, boolean enabled) {
    }

    record Problem(String code, Map<String, String> args, String message) {
    }

    @GetMapping
    OverviewJson overview() {
        return review.configured() ? json(review.overview()) : new OverviewJson(false, null, null, List.of());
    }

    @PostMapping("/read")
    OverviewJson read() throws CalendarUnavailableException {
        OverviewJson read = json(review.read());
        changed();
        return read;
    }

    @ExceptionHandler(CalendarUnavailableException.class)
    ResponseEntity<Problem> unavailable(CalendarUnavailableException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new Problem("calendarUnavailable", Map.of(), e.getMessage()));
    }

    @PostMapping("/events/{id}/decide")
    void decide(@PathVariable String id, @RequestBody Verdict verdict) {
        review.decide(id, verdict.kind());
        changed();
    }

    @PostMapping("/events/{id}/forget")
    void forget(@PathVariable String id) {
        review.forget(id);
        changed();
    }

    @PostMapping("/events/{id}/seen")
    void seen(@PathVariable String id) {
        review.seen(id);
        changed();
    }

    @PostMapping("/seen")
    void seenAll() {
        review.seenAll();
        changed();
    }

    @PostMapping("/events/{id}/gig")
    void addToCatalog(@PathVariable String id, @RequestBody AddToCatalog request) {
        Gig gig = InvalidGigException.gigOf(request.gig());
        catalog.addToCatalog(id, gig);
        changed();
        live.changed(LiveUpdates.Topic.GIGS);
    }

    @PostMapping("/events/{id}/link")
    void link(@PathVariable String id, @RequestBody Link link) {
        catalog.link(id, GigId.fromToken(link.gig()));
        changed();
    }

    @PostMapping("/events/{id}/unlink")
    void unlink(@PathVariable String id) {
        catalog.unlink(id);
        changed();
    }

    @PostMapping("/link-same-day")
    Linked linkSameDay() {
        Linked linked = new Linked(catalog.linkSameDayGigs());
        changed();
        return linked;
    }

    @GetMapping("/rules")
    List<RuleJson> rules() {
        return review.rules().stream().map(r -> new RuleJson(r.kind().name(), r.value(), r.weight(),
                r.kind().weighted(), r.origin().name(), r.enabled())).toList();
    }

    private void changed() {
        live.changed(LiveUpdates.Topic.CALENDAR);
    }

    private OverviewJson json(CalendarOverview overview) {
        LocalDate today = LocalDate.now(clock);
        List<CalendarRow> rows = overview.rows().stream()
                .sorted(Comparator.comparing((CalendarRow r) -> r.event().start()).reversed()).toList();
        return new OverviewJson(true, iso(overview.lastRead()), CalendarFilter.count(rows, today),
                rows.stream().map(r -> row(r, today)).toList());
    }

    private static RowJson row(CalendarRow r, LocalDate today) {
        CalendarEvent e = r.event();
        CalendarClassification c = r.classification();
        CalendarGigDraft d = r.draft();
        CatalogMatch m = r.match();
        CalendarChange change = r.change();
        return new RowJson(e.id(), e.title(), e.location(), e.notes(), e.start().toString(), e.end().toString(),
                e.allDay(), c.kind().name(), c.suggested().name(), c.decidedByUser(), c.status().name(), c.score(),
                c.reasons().stream().map(x -> new ReasonJson(x.why().name(), x.value(), x.weight(), x.text())).toList(),
                new DraftJson(d.title(), d.date(), time(d.showTime()), time(d.eventStart()), d.venue(), d.city(),
                        d.country(), d.street(), d.postalCode()),
                new MatchJson(m.state().name(), ref(m.gig()), m.sameDay().stream().map(CalendarEndpoint::ref).toList(),
                        m.differences()),
                change == null ? null : new ChangeJson(change.type().name(),
                        change.fields().stream().map(Enum::name).sorted().toList(),
                        change.suggestedBefore() == null ? null : change.suggestedBefore().name(), iso(change.at())),
                r.removed(), r.missingFromCatalog(), r.needsAttention(),
                Arrays.stream(CalendarFilter.values()).filter(f -> f.on(today).test(r)).map(Enum::name).toList());
    }

    private static GigRef ref(Gig gig) {
        return gig == null ? null : new GigRef(gig.id().token(), gig.title(),
                gig.schedule().start().toLocalDateTime().toString(), gig.location().city(), gig.cancelled());
    }

    private static String time(java.time.LocalTime time) {
        return time == null ? null : time.toString();
    }

    private static String iso(Instant at) {
        return at == null ? null : at.toString();
    }
}
