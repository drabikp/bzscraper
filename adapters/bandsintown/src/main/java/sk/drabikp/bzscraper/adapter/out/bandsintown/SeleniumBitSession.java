package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.adapter.out.browser.PlatformBrowser;
import sk.drabikp.bzscraper.application.port.out.FailureKind;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ImportedGig;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One logged-in artist-portal browser. Works only through the portal's own pages — each one
 * a page object ({@link BitEventsPage}, {@link BitBulkUpload}, {@link BitEventForm},
 * {@link BitDeleteDialog}) — and reads the portal's replies to them ({@link PortalReply}):
 * <ul>
 *   <li><b>create</b> — Bulk Upload of a CSV without event ids (≤ 25 rows) creates drafts,
 *       published with "Notify my followers" as configured; the reply maps each row to its
 *       new event id; the event lists are read back.</li>
 *   <li><b>update</b> — Bulk Upload of rows WITH event ids; past events (which the upload
 *       refuses) through the event's form.</li>
 *   <li><b>delete</b> — the Upcoming row's "⋯" → Delete, or (past events) the form's Delete,
 *       with a reason ("canceled" or "other").</li>
 *   <li><b>list</b> — the Upcoming and Past lists.</li>
 * </ul>
 * A failure leaves a screenshot of the page behind; anything unexpected from the browser is a
 * temporary failure.
 */
final class SeleniumBitSession implements BitSession {

    static final int MAX_ROWS_PER_UPLOAD = 25;
    private static final Logger logger = LoggerFactory.getLogger(SeleniumBitSession.class);

    private final BitPortal portal;
    private final BitEventsPage events;
    private final String artistName;
    private final PlatformBrowser.Lease lease;
    private final Clock clock;
    private boolean closed;

    SeleniumBitSession(PlatformBrowser.Lease lease, WebDriverWait wait, HumanPacer pacer, String baseUrl,
                       String artistId, String artistName, Clock clock) {
        this.lease = lease;
        this.clock = clock;
        this.portal = new BitPortal(lease.driver(), wait, pacer, baseUrl, artistId);
        this.events = new BitEventsPage(portal);
        this.artistName = artistName;
    }

    // --- create ---

    @Override
    public List<Created> createEvents(List<Gig> gigs, boolean notifyFollowers) {
        List<Created> results = new ArrayList<>();
        for (int from = 0; from < gigs.size(); from += MAX_ROWS_PER_UPLOAD) {
            List<Gig> batch = gigs.subList(from, Math.min(gigs.size(), from + MAX_ROWS_PER_UPLOAD));
            if (from > 0) {
                portal.longPause();
            }
            try {
                results.addAll(guarded("upload", "Bandsintown upload failed", () -> createBatch(batch, notifyFollowers)));
            } catch (BitUploadException e) {
                logger.warn("Bandsintown upload of {} gig(s) failed", batch.size(), e);
                batch.forEach(gig -> results.add(Created.failed(gig, e.kind(), e.getMessage())));
            }
        }
        return results;
    }

    private List<Created> createBatch(List<Gig> batch, boolean notifyFollowers) throws BitUploadException {
        BitBulkUpload upload = events.bulkUpload();
        PortalReply reply = upload.upload(BandsintownCsv.newEvents(batch, artistName, notifyFollowers));
        if (!reply.ok()) {
            throw reply.rowErrors().isEmpty()
                    ? new BitUploadException("Bandsintown rejected the upload — " + reply.describe())
                    : BitUploadException.refused("Bandsintown refused the events: " + String.join(", ", reply.rowErrors()));
        }
        String[] drafts = reply.draftIds(batch.size());
        String[] existing = reply.updatedIds(batch.size());
        if (Arrays.stream(drafts).anyMatch(Objects::nonNull)) {
            upload.publishDrafts(notifyFollowers);
        } else {
            upload.ok();                            // every row matched an existing event
        }

        Map<String, BitEvent> listed = listed(batch);
        List<Created> results = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            Gig gig = batch.get(i);
            String id = drafts[i] != null ? drafts[i] : existing[i];
            BitEvent event = id == null ? null : listed.get(id);
            if (id == null) {
                results.add(Created.failed(gig, FailureKind.REFUSED,
                        "Bandsintown rejected this row — " + reply.describe()));
            } else if (event != null && event.published()) {
                results.add(Created.published(gig, id, BitPlaces.check(gig, event)));
            } else {
                results.add(Created.failed(gig, FailureKind.NEEDS_USER, "Uploaded to Bandsintown as event " + id + ", but it is not "
                        + "published (" + (event == null ? "not listed" : event.status()) + ") — publish or delete it there."));
            }
        }
        return results;
    }

    /**
     * The listed events by id: the upcoming list, plus the past list when the batch has gigs
     * already played (published past events are listed there, not as upcoming).
     */
    private Map<String, BitEvent> listed(List<Gig> batch) throws BitUploadException {
        Map<String, BitEvent> byId = new HashMap<>();
        events.upcoming().forEach(e -> byId.put(e.id(), e));
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (batch.stream().anyMatch(gig -> gig.schedule().showStart().isBefore(now))) {
            portal.pause();
            events.past().forEach(e -> byId.putIfAbsent(e.id(), e));
        }
        return byId;
    }

    // --- update ---

    /**
     * One upload of rows WITH event ids. Each row is judged on its own: updated (its id in
     * {@code updated_events}), refused (Bandsintown's row error), or not applied — a reply
     * with errors may hold back the other rows too, so those simply try again.
     */
    @Override
    public List<Edited> updateEvents(List<Map.Entry<String, Gig>> edits) throws BitUploadException {
        return guarded("update", "Bandsintown upload failed", () -> {
            Map<String, Gig> ordered = new LinkedHashMap<>();
            edits.forEach(edit -> ordered.put(edit.getKey(), edit.getValue()));
            BitBulkUpload upload = events.bulkUpload();
            PortalReply reply = upload.upload(BandsintownCsv.updates(ordered, artistName));
            int rows = edits.size();
            String[] updated = reply.updatedIds(rows);
            String[] drafts = reply.draftIds(rows);
            String[] refused = reply.rowErrorsByIndex(rows);
            if (!reply.ok() && Arrays.stream(refused).allMatch(Objects::isNull)) {
                throw new BitUploadException("Bandsintown rejected the upload — " + reply.describe());
            }
            List<Edited> results = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                String eventId = edits.get(i).getKey();
                if (drafts[i] != null) {
                    results.add(Edited.notUpdated(eventId, FailureKind.NEEDS_USER, "Bandsintown made a new draft " + drafts[i]
                            + " instead of editing event " + eventId + " — delete that draft there"));
                } else if (refused[i] != null) {
                    results.add(Edited.notUpdated(eventId, FailureKind.REFUSED, "Bandsintown refused it: " + refused[i]));
                } else if (eventId.equals(updated[i])) {
                    results.add(Edited.updated(eventId));
                } else {
                    results.add(Edited.notUpdated(eventId, FailureKind.TEMPORARY, "not applied — " + reply.describe()));
                }
            }
            if (reply.ok()) {
                upload.ok();
            }
            return results;
        });
    }

    /**
     * The event's single-page form, which also opens for past events: the place from the
     * venue search (the suggestion in the gig's town), then dates, times, name and
     * description; saved. A note when Bandsintown placed it far from the town, else null.
     */
    @Override
    public String editEventInForm(String eventId, Gig gig) throws BitUploadException {
        return guarded("form-edit", "Bandsintown's edit form failed", () -> {
            BitEventForm form = BitEventForm.open(portal, eventId).orElseThrow(() ->
                    new BitUploadException("Bandsintown's form for event " + eventId + " didn't open."));
            form.pickVenue(gig);
            form.setSchedule(gig.schedule().showStart(), gig.schedule().showEnd());
            form.setTitle(gig.title());
            if (gig.description() != null) {
                form.setDescription(gig.description());
            }
            PortalReply reply = form.save();
            return reply.event().map(saved -> BitPlaces.check(gig, saved)).orElse(null);
        });
    }

    // --- delete ---

    /** The Upcoming row's "⋯" → Delete. A past event isn't there: refused (the form can). */
    @Override
    public void deleteEvent(String eventId, RemovalReason reason) throws BitUploadException {
        guarded("delete", "Bandsintown delete failed", () -> {
            List<BitEvent> upcoming = events.upcoming();
            Optional<BitEvent> event = upcoming.stream().filter(e -> eventId.equals(e.id())).findFirst();
            if (event.isEmpty()) {
                portal.pause();
                if (events.past().stream().anyMatch(e -> eventId.equals(e.id()))) {
                    throw BitUploadException.refused("Bandsintown event " + eventId + " is a past event, which the "
                            + "event list can't remove (its form can).");
                }
                logger.info("Bandsintown event {} is not listed — already removed", eventId);
                return null;
            }
            portal.pause();
            events.delete(event.get(), upcoming).confirm(eventId, reason);
            return null;
        });
    }

    /**
     * The event's form (it opens for past events too) → Delete → the same dialog as the
     * list's. A form that doesn't open for an event neither list shows: already gone.
     */
    @Override
    public void deleteEventInForm(String eventId, RemovalReason reason) throws BitUploadException {
        guarded("form-delete", "Bandsintown's form delete failed", () -> {
            Optional<BitEventForm> form = BitEventForm.open(portal, eventId);
            if (form.isEmpty()) {
                portal.pause();
                if (events.find(eventId).isPresent()) {
                    throw new BitUploadException("Bandsintown's form for event " + eventId + " didn't open — nothing "
                            + "was deleted.");
                }
                logger.info("Bandsintown event {} is not listed — already removed", eventId);
                return null;
            }
            form.get().delete().confirm(eventId, reason);
            return null;
        });
    }

    // --- list (import) ---

    @Override
    public List<ImportedGig> listEvents() throws BitUploadException {
        return guarded("list", "Could not read the Bandsintown events", () -> {
            Map<String, BitEvent> byId = new LinkedHashMap<>();
            events.upcoming().forEach(e -> byId.putIfAbsent(e.id(), e));
            portal.pause();
            events.past().forEach(e -> byId.putIfAbsent(e.id(), e));
            List<ImportedGig> gigs = new ArrayList<>();
            byId.values().forEach(e -> BitEventMapper.toImported(e).ifPresent(gigs::add));
            return gigs;
        });
    }

    /**
     * Read-only: what the portal lists for the given event ids, upcoming and past — "status
     * start_date at venue … (upcoming|past)", or nothing for an id it doesn't list.
     */
    Map<String, String> inspect(Collection<String> ids) throws BitUploadException {
        Map<String, String> found = new LinkedHashMap<>();
        events.upcoming().stream().filter(e -> ids.contains(e.id()))
                .forEach(e -> found.put(e.id(), e.described() + " (upcoming)"));
        portal.pause();
        events.past().stream().filter(e -> ids.contains(e.id()))
                .forEach(e -> found.putIfAbsent(e.id(), e.described() + " (past)"));
        return found;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        lease.keepWarm();                           // kept open a little for the next session
    }

    /** Runs one operation; a failure leaves a screenshot, and a browser error is a temporary failure. */
    private <T> T guarded(String step, String failure, PlatformBrowser.Work<T, BitUploadException> work)
            throws BitUploadException {
        return lease.guarded(step, work, e -> new BitUploadException(failure + ": " + e.getMessage(), e));
    }
}
