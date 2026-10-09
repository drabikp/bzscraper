package sk.drabikp.bzscraper.calendar.application;

import sk.drabikp.bzscraper.calendar.application.port.in.CalendarCatalogUseCase;
import sk.drabikp.bzscraper.calendar.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.calendar.domain.CalendarCatalogMatcher;
import sk.drabikp.bzscraper.calendar.domain.CalendarChanges;
import sk.drabikp.bzscraper.calendar.domain.CalendarGigDraft;
import sk.drabikp.bzscraper.calendar.domain.CalendarGigDrafter;
import sk.drabikp.bzscraper.calendar.domain.CalendarOverview;
import sk.drabikp.bzscraper.calendar.domain.CalendarRow;
import sk.drabikp.bzscraper.calendar.domain.CatalogMatch;
import sk.drabikp.bzscraper.calendar.domain.KnownCalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.BandProfile;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarEventClassifier;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calendar orchestrator. Reads the calendar, classifies its events with the stored band
 * profile ({@link CalendarEventClassifier}, the catalog's gig days as evidence), keeps the
 * user's verdicts, and saves each read so the next one shows what is new, changed or gone
 * ({@link CalendarChanges}). Matches every event against the catalog
 * ({@link CalendarCatalogMatcher}) and brings gigs in: added from a pre-filled form
 * ({@link CalendarGigDrafter}) or linked to the gig already there. Never writes the
 * calendar; changes to catalog gigs go through the catalog use cases (and so the outbox).
 */
public class CalendarReviewService implements ReviewCalendarUseCase, CalendarCatalogUseCase {

    private final GigWrites writes;
    private final CalendarFeed feed;
    private final BandProfileStore profileStore;
    private final CalendarDecisionStore decisionStore;
    private final CalendarSnapshotStore snapshotStore;
    private final CalendarLinkStore linkStore;
    private final GigRepository gigRepository;
    private final Transactions transactions;
    private final Clock clock;
    private final BandProfile.Thresholds thresholds;

    public CalendarReviewService(CalendarFeed feed, BandProfileStore profileStore, CalendarDecisionStore decisionStore,
                                 CalendarSnapshotStore snapshotStore, CalendarLinkStore linkStore,
                                 GigRepository gigRepository, Transactions transactions, Clock clock,
                                 BandProfile.Thresholds thresholds, GigWrites writes) {
        this.writes = writes;
        this.feed = feed;
        this.profileStore = profileStore;
        this.decisionStore = decisionStore;
        this.snapshotStore = snapshotStore;
        this.linkStore = linkStore;
        this.gigRepository = gigRepository;
        this.transactions = transactions;
        this.clock = clock;
        this.thresholds = thresholds;
    }

    @Override
    public boolean configured() {
        return feed.configured();
    }

    @Override
    public CalendarOverview read() throws CalendarUnavailableException {
        List<CalendarEvent> fresh = feed.read();
        Instant now = clock.instant();
        transactions.inTransaction(() -> {
            List<CalendarClassification> classified = classify(fresh, profile(), gigRepository.findAll());
            snapshotStore.saveRead(CalendarChanges.afterRead(snapshotStore.all(), classified, now), now);
        });
        return overview();
    }

    @Override
    public CalendarOverview overview() {
        Map<String, KnownCalendarEvent> known = snapshotStore.all();
        List<CalendarEvent> shown = known.values().stream()
                .filter(k -> !k.removed() || k.change() != null)
                .map(KnownCalendarEvent::event)
                .toList();
        BandProfile profile = profile();
        List<Gig> gigs = gigRepository.findAll();
        Map<GigId, Gig> catalog = gigs.stream().collect(Collectors.toMap(Gig::id, Function.identity(), (a, b) -> a));
        Map<String, GigId> links = linkStore.all();
        LocalDate today = LocalDate.now(clock);
        List<CalendarRow> rows = new ArrayList<>();
        for (CalendarClassification c : classify(shown, profile, gigs)) {
            KnownCalendarEvent k = known.get(c.event().id());
            CalendarGigDraft draft = CalendarGigDrafter.draft(c.event(), profile);
            CatalogMatch match = CalendarCatalogMatcher.match(draft, c.status(), k.removed(), links.get(k.id()),
                    catalog, today);
            rows.add(new CalendarRow(c, draft, match, k.change(), k.removed()));
        }
        return new CalendarOverview(snapshotStore.lastRead().orElse(null), rows);
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
    public void seen(String eventId) {
        transactions.inTransaction(() -> snapshotStore.find(eventId).ifPresent(this::markSeen));
    }

    @Override
    public void seenAll() {
        transactions.inTransaction(() -> snapshotStore.all().values().stream()
                .filter(k -> k.change() != null)
                .forEach(this::markSeen));
    }

    private void markSeen(KnownCalendarEvent known) {
        if (known.removed()) {
            snapshotStore.remove(known.id());
            linkStore.unlink(known.id());
        } else if (known.change() != null) {
            snapshotStore.save(known.seen());
        }
    }

    @Override
    public List<ProfileRule> rules() {
        return profileStore.rules();
    }

    @Override
    public void addToCatalog(String eventId, Gig gig) {
        transactions.inTransaction(() -> {
            if (gigRepository.findById(gig.id()).isPresent()) {
                throw new UserFacingException("The catalog already has a gig on " + gig.schedule().startDate()
                        + " at " + gig.location().displayVenue() + " — link the event to it instead");
            }
            writes.add(gig);
            linkStore.link(eventId, gig.id());
        });
    }

    @Override
    public void link(String eventId, GigId gigId) {
        transactions.inTransaction(() -> {
            if (gigRepository.findById(gigId).isEmpty()) {
                throw new UserFacingException("That gig is no longer in the catalog.");
            }
            linkStore.link(eventId, gigId);
        });
    }

    @Override
    public void unlink(String eventId) {
        linkStore.unlink(eventId);
    }

    @Override
    public int linkSameDayGigs() {
        CalendarOverview overview = overview();
        Set<GigId> taken = overview.rows().stream()
                .filter(r -> r.match().state() == CatalogMatch.State.LINKED)
                .map(r -> r.match().gig().id())
                .collect(Collectors.toCollection(HashSet::new));
        List<CalendarRow> candidates = overview.rows().stream()
                .filter(r -> r.missingFromCatalog() && r.match().gig() != null && !taken.contains(r.match().gig().id()))
                .toList();
        // two gig events on a day with one catalog gig: the user picks
        Map<GigId, Long> claims = candidates.stream()
                .collect(Collectors.groupingBy(r -> r.match().gig().id(), Collectors.counting()));
        List<CalendarRow> clear = candidates.stream().filter(r -> claims.get(r.match().gig().id()) == 1).toList();
        transactions.inTransaction(() -> clear.forEach(r -> linkStore.link(r.eventId(), r.match().gig().id())));
        return clear.size();
    }

    private List<CalendarClassification> classify(List<CalendarEvent> events, BandProfile profile,
                                                  Collection<Gig> gigs) {
        Set<LocalDate> catalogDays = gigs.stream().flatMap(gig -> gig.schedule().days().stream())
                .collect(Collectors.toSet());
        return CalendarEventClassifier.classify(events, profile, catalogDays, decisionStore.all());
    }

    private BandProfile profile() {
        return new BandProfile(profileStore.rules(), thresholds);
    }
}
