package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.domain.model.Address;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ImportedGig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One logged-in artist-portal browser. Works only through the portal's own pages:
 * <ul>
 *   <li><b>create</b> — Bulk Upload of a CSV without event ids (≤ 25 rows) creates drafts;
 *       the success dialog's "Notify my followers" switch is set as configured and
 *       "Publish" pressed. The upload reply maps each CSV row to its new event id.</li>
 *   <li><b>update</b> — Bulk Upload of a CSV row WITH the event id edits that event.</li>
 *   <li><b>delete</b> — the event's "⋯" menu → Delete, with a reason
 *       ("canceled" or "other").</li>
 *   <li><b>list</b> — the Upcoming and Past tabs' event lists; the past list comes 20 at a
 *       time ({@code x-next-page}), and more load as the page is scrolled.</li>
 * </ul>
 * The portal's own replies are read by wrapping {@code window.fetch} in the page (a
 * hand-made API call would lack the portal's request signature).
 */
final class SeleniumBitSession implements BitSession {

    static final int MAX_ROWS_PER_UPLOAD = 25;
    private static final Logger logger = LoggerFactory.getLogger(SeleniumBitSession.class);
    private static final Duration REPLY_WAIT = Duration.ofSeconds(90);

    /**
     * Records the portal's event replies in {@code window.__bz}. Registered to run before
     * any page script (see {@link SeleniumBitPortalClient}), so even the lists a page
     * fetches while loading are seen; also run after load as a fallback.
     */
    static final String CAPTURE_REPLIES = """
            (() => {
            if (window.__bzHooked) return;
            window.__bzHooked = true;
            window.__bz = [];
            const original = window.fetch;
            window.fetch = function (u, o) {
              const url = String(u && u.url || u);
              const method = String((o && o.method) || (u && u.method) || 'GET').toUpperCase();
              const reply = original.apply(this, arguments);
              if (/\\/managed-actors\\/\\d+\\/events|\\/api\\//.test(url)) {
                reply.then(r => r.clone().text().then(t => {
                  let json = null;
                  try { json = JSON.parse(t); } catch (e) {}
                  window.__bz.push({method, url, status: r.status, json, text: t.slice(0, 2000),
                                    next: r.headers.get('x-next-page')});
                })).catch(e => window.__bz.push({method, url, status: -1, json: null, text: String(e)}));
              }
              return reply;
            };
            })();""";

    /** Marks the "⋯" button of the index-th listed event after checking its city and day. */
    private static final String MARK_ROW = """
            const [index, city, day, expected] = arguments;
            const iconOnly = b => !b.innerText.trim() && b.querySelector('svg') && b.getBoundingClientRect().width > 0;
            const rows = [];
            for (const b of document.querySelectorAll('button')) {
              if (!iconOnly(b)) continue;
              let r = b.parentElement;
              while (r && !/RSVP/.test(r.innerText || '')) r = r.parentElement;
              // an event row holds exactly one icon-only button: its "⋯" menu
              if (!r || r.innerText.length >= 500) continue;
              if ([...r.querySelectorAll('button')].filter(iconOnly).length !== 1) continue;
              if (!rows.some(x => x.r === r)) rows.push({r, b});
            }
            if (rows.length !== expected) return 'expected ' + expected + ' event rows, found ' + rows.length;
            const text = rows[index].r.innerText;
            if (!text.includes(city)) return 'row ' + index + ' is not in ' + city + ': ' + text.slice(0, 80);
            if (!new RegExp('(^|\\\\D)0?' + day + '(\\\\D|$)').test(text)) return 'row ' + index + ' is not on day ' + day;
            document.querySelectorAll('[data-bz-kebab]').forEach(e => e.removeAttribute('data-bz-kebab'));
            rows[index].b.setAttribute('data-bz-kebab', '1');
            return 'ok';""";

    /** Reads the success dialog's "Notify my followers" switch and marks what to click. */
    private static final String READ_NOTIFY_SWITCH = """
            const box = document.querySelector('.notify');
            if (!box) return null;
            const input = box.querySelector('input[type=checkbox]');
            const sw = input || box.querySelector('[role=switch],[aria-checked]');
            if (!sw) return null;
            document.querySelectorAll('[data-bz-notify]').forEach(e => e.removeAttribute('data-bz-notify'));
            (input ? (input.closest('label') || input.parentElement) : sw).setAttribute('data-bz-notify', '1');
            return input ? input.checked : sw.getAttribute('aria-checked') === 'true';""";

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final HumanPacer pacer;
    private final String baseUrl;
    private final String artistId;
    private final String artistName;
    private final Consumer<WebDriver> release;
    private boolean closed;

    SeleniumBitSession(WebDriver driver, WebDriverWait wait, HumanPacer pacer, String baseUrl,
                       String artistId, String artistName, Consumer<WebDriver> release) {
        this.driver = driver;
        this.wait = wait;
        this.pacer = pacer;
        this.baseUrl = baseUrl;
        this.artistId = artistId;
        this.artistName = artistName;
        this.release = release;
    }

    // --- create ---

    @Override
    public List<Created> createEvents(List<Gig> gigs, boolean notifyFollowers) {
        List<Created> results = new ArrayList<>();
        for (int from = 0; from < gigs.size(); from += MAX_ROWS_PER_UPLOAD) {
            List<Gig> batch = gigs.subList(from, Math.min(gigs.size(), from + MAX_ROWS_PER_UPLOAD));
            if (from > 0) {
                pacer.longPause();
            }
            try {
                results.addAll(createBatch(batch, notifyFollowers));
            } catch (BitUploadException | RuntimeException e) {
                logger.warn("Bandsintown upload of {} gig(s) failed", batch.size(), e);
                SeleniumBitPortalClient.saveScreenshot(driver, "upload");
                String reason = e instanceof BitUploadException ? e.getMessage()
                        : "Bandsintown upload failed: " + e.getMessage();
                batch.forEach(gig -> results.add(Created.failed(gig, reason)));
            }
        }
        return results;
    }

    private List<Created> createBatch(List<Gig> batch, boolean notifyFollowers) throws BitUploadException {
        Map<String, Object> reply = upload(BandsintownCsv.newEvents(batch, artistName, notifyFollowers));
        if (!BitResponses.ok(reply)) {
            List<String> refused = BitResponses.rowErrors(reply);
            throw refused.isEmpty()
                    ? new BitUploadException("Bandsintown rejected the upload — " + BitResponses.describe(reply))
                    : new BitUploadException("Bandsintown refused the events: " + String.join(", ", refused), null, true);
        }
        String[] drafts = BitResponses.draftIds(reply, batch.size());
        String[] existing = BitResponses.updatedIds(reply, batch.size());
        if (Arrays.stream(drafts).anyMatch(Objects::nonNull)) {
            publishUploadedDrafts(notifyFollowers);
        } else {
            clickOk();                              // every row matched an existing event
        }

        Map<String, Map<String, Object>> listed = listed(batch);
        List<Created> results = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            Gig gig = batch.get(i);
            String id = drafts[i] != null ? drafts[i] : existing[i];
            if (id == null) {
                results.add(Created.failed(gig, "Bandsintown rejected this row — " + BitResponses.describe(reply)));
            } else if (listed.containsKey(id) && "PUBLISHED".equals(String.valueOf(listed.get(id).get("status")))) {
                results.add(Created.published(gig, id, placeCheck(gig, listed.get(id))));
            } else {
                Object status = listed.containsKey(id) ? listed.get(id).get("status") : "not listed";
                results.add(Created.failed(gig, "Uploaded to Bandsintown as event " + id + ", but it is not "
                        + "published (" + status + ") — publish or delete it there."));
            }
        }
        return results;
    }

    private void publishUploadedDrafts(boolean notifyFollowers) throws BitUploadException {
        setNotifySwitch(notifyFollowers);
        int mark = replyCount();
        pacer.pause();
        pacer.click(driver, visibleButton("Publish"));
        Map<String, Object> reply = awaitReply(mark, r -> !"GET".equals(r.get("method")), "publishing");
        if (BitResponses.httpStatus(reply) != 200) {
            throw new BitUploadException("Bandsintown did not publish the uploaded events — "
                    + BitResponses.describe(reply) + " They are left as drafts there.");
        }
        pacer.pause();
    }

    private void setNotifySwitch(boolean desired) throws BitUploadException {
        Object state = js(READ_NOTIFY_SWITCH);
        if (state == null) {
            throw new BitUploadException("Could not find Bandsintown's \"Notify my followers\" switch, so "
                    + "nothing was published — the events are left as drafts there.");
        }
        if (!Boolean.valueOf(desired).equals(state)) {
            pacer.pause();
            pacer.click(driver, driver.findElement(By.cssSelector("[data-bz-notify]")));
            if (!Boolean.valueOf(desired).equals(js(READ_NOTIFY_SWITCH))) {
                throw new BitUploadException("Could not switch Bandsintown's \"Notify my followers\" "
                        + (desired ? "on" : "off") + ", so nothing was published — the events are left as drafts there.");
            }
        }
    }

    // --- update ---

    @Override
    public void updateEvent(String eventId, Gig gig) throws BitUploadException {
        try {
            update(eventId, gig);
        } catch (BitUploadException | RuntimeException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "update");
            throw e;
        }
    }

    private void update(String eventId, Gig gig) throws BitUploadException {
        Map<String, Object> reply = upload(BandsintownCsv.updates(Map.of(eventId, gig), artistName));
        String updated = BitResponses.updatedIds(reply, 1)[0];
        String draft = BitResponses.draftIds(reply, 1)[0];
        if (draft != null) {
            throw new BitUploadException("Bandsintown did not recognise event " + eventId + " and created a new "
                    + "draft " + draft + " instead — delete that draft on Bandsintown.");
        }
        List<String> refused = BitResponses.rowErrors(reply);
        if (!refused.isEmpty()) {
            throw new BitUploadException("Bandsintown refused the change to event " + eventId + ": "
                    + String.join(", ", refused), null, true);
        }
        if (!BitResponses.ok(reply) || !eventId.equals(updated)) {
            throw new BitUploadException("Bandsintown did not update event " + eventId + " — "
                    + BitResponses.describe(reply));
        }
        clickOk();
    }

    @Override
    public List<Edited> updateEvents(List<Map.Entry<String, Gig>> edits) throws BitUploadException {
        try {
            return updates(edits);
        } catch (BitUploadException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "update");
            throw e;
        } catch (RuntimeException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "update");
            throw new BitUploadException("Bandsintown upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * One upload of rows WITH event ids. Each row is judged on its own: updated (its id in
     * {@code updated_events}), refused (Bandsintown's row error), or not applied — a reply
     * with errors may hold back the other rows too, so those simply try again.
     */
    private List<Edited> updates(List<Map.Entry<String, Gig>> edits) throws BitUploadException {
        Map<String, Gig> ordered = new LinkedHashMap<>();
        edits.forEach(edit -> ordered.put(edit.getKey(), edit.getValue()));
        Map<String, Object> reply = upload(BandsintownCsv.updates(ordered, artistName));
        int rows = edits.size();
        String[] updated = BitResponses.updatedIds(reply, rows);
        String[] drafts = BitResponses.draftIds(reply, rows);
        String[] refused = BitResponses.rowErrorsByIndex(reply, rows);
        if (!BitResponses.ok(reply) && Arrays.stream(refused).allMatch(Objects::isNull)) {
            throw new BitUploadException("Bandsintown rejected the upload — " + BitResponses.describe(reply));
        }
        List<Edited> results = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            String eventId = edits.get(i).getKey();
            if (drafts[i] != null) {
                results.add(new Edited(eventId, null, "Bandsintown made a new draft " + drafts[i]
                        + " instead of editing event " + eventId + " — delete that draft there"));
            } else if (refused[i] != null) {
                results.add(new Edited(eventId, refused[i], null));
            } else if (eventId.equals(updated[i])) {
                results.add(new Edited(eventId, null, null));
            } else {
                results.add(new Edited(eventId, null, "not applied — " + BitResponses.describe(reply)));
            }
        }
        if (BitResponses.ok(reply)) {
            clickOk();
        }
        return results;
    }

    // --- edit in the single-page form (past events too) ---

    private static final DateTimeFormatter FORM_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORM_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    @Override
    public String editEventInForm(String eventId, Gig gig) throws BitUploadException {
        try {
            return formEdit(eventId, gig);
        } catch (BitUploadException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "form-edit");
            throw e;
        } catch (RuntimeException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "form-edit");
            throw new BitUploadException("Bandsintown's edit form failed: " + e.getMessage(), e);
        }
    }

    /**
     * The event's single-page edit form ({@code ?version=single-page}), which also opens for
     * past events: the place is picked from Bandsintown's venue search (Google places) — the
     * suggestion in the gig's town — then dates, times, name and description are set where
     * they differ, and the form is saved. Returns a note when Bandsintown placed it far from
     * the town, else null.
     */
    private String formEdit(String eventId, Gig gig) throws BitUploadException {
        driver.get(baseUrl + "/artists/" + artistId + "/events/" + eventId + "?version=single-page");
        WebElement venue = wait.until(ExpectedConditions.presenceOfElementLocated(By.name("venue_name")));
        js(CAPTURE_REPLIES);
        pacer.pause();

        pickVenue(venue, gig);
        ZonedDateTime start = gig.schedule().showStart();
        ZonedDateTime end = gig.schedule().showEnd();
        setField(By.name("start_date"), start.format(FORM_DATE));
        setField(By.name("start_time"), start.format(FORM_TIME));
        if (end != null) {
            setField(By.name("end_date"), end.format(FORM_DATE));
            setField(By.name("end_time"), end.format(FORM_TIME));
        }
        WebElement title = (WebElement) js("var l = Array.from(document.querySelectorAll('label')).find(x =>"
                + " x.innerText.replace('*', '').trim() === 'Event Name'); return l ? document.getElementById(l.htmlFor) : null;");
        if (title != null) {
            setField(title, gig.title());
        }
        WebElement description = firstVisible(By.cssSelector("textarea[placeholder='Description'], textarea[name='description']"));
        if (description != null && gig.description() != null) {
            setField(description, gig.description());
        }
        int mark = replyCount();
        pacer.pause();
        pacer.click(driver, visibleButton("Save"));
        Map<String, Object> reply = awaitReply(mark,
                r -> !"GET".equals(r.get("method")) && String.valueOf(r.get("url")).contains(eventId), "saving the form");
        if (BitResponses.httpStatus(reply) != 200 || !BitResponses.ok(reply)) {
            List<String> refused = BitResponses.rowErrors(reply);
            throw new BitUploadException("Bandsintown didn't save event " + eventId + " — "
                    + (refused.isEmpty() ? BitResponses.describe(reply) : String.join(", ", refused)), null, true);
        }
        Map<String, Object> saved = BitResponses.payloadOf(reply);
        return saved == null ? null : placeCheck(gig, saved);
    }

    /** Searches the venue ("venue town") and picks the suggestion in the gig's town; none → a refusal. */
    @SuppressWarnings("unchecked")
    private void pickVenue(WebElement field, Gig gig) throws BitUploadException {
        String city = gig.location().city();
        String query = gig.location().venue() != null ? gig.location().venue() + " " + city
                : city + " " + gig.location().countryName();
        int mark = replyCount();
        // the field's own "Clear value" drops the old place; typing over it would keep it
        WebElement clear = (WebElement) js("var e = arguments[0]; for (var i = 0; i < 4 && e.parentElement; i++)"
                + " e = e.parentElement; return e.querySelector(\"button[aria-label='Clear value']\");", field);
        if (clear != null) {
            pacer.click(driver, clear);
            pacer.pause();
        }
        field = driver.findElement(By.name("venue_name"));
        pacer.type(driver, field, query);
        Map<String, Object> reply = awaitReply(mark, r -> String.valueOf(r.get("url")).contains("/venues/autocomplete"),
                "the venue search");
        Object payload = reply.get("json") instanceof Map<?, ?> json ? json.get("payload") : null;
        List<Map<String, Object>> places = payload instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        Map<String, Object> place = BitPlaces.choose(places, gig.location().venue(), city);
        if (place == null) {
            throw new BitUploadException("Bandsintown's venue search (Google places) has nothing for '" + query
                    + "' in " + city + " — set the place there by hand.", null, true);
        }
        String placeId = String.valueOf(place.get("place_id"));
        WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("li[role='option'][value='" + placeId.replace("'", "") + "'] button")));
        pacer.click(driver, option);
        pacer.pause();
        String picked = driver.findElement(By.name("venue_name")).getDomProperty("value");
        if (!String.valueOf(place.get("description")).equals(picked)) {
            throw new BitUploadException("Bandsintown's venue field didn't take the place ('" + picked
                    + "') — nothing was saved.");
        }
    }

    private void setField(By by, String value) {
        setField(wait.until(ExpectedConditions.presenceOfElementLocated(by)), value);
    }

    /** Types the value when the field holds something else; a picker's matching option is clicked. */
    private void setField(WebElement field, String value) {
        if (value.equals(field.getDomProperty("value"))) {
            return;
        }
        pacer.click(driver, field);
        field.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        pacer.typeIntoFocused(driver, value);
        pacer.pause();
        driver.findElements(By.xpath("//*[@role='option' or self::li]")).stream()
                .filter(WebElement::isDisplayed).filter(e -> value.equals(e.getText().trim()))
                .findFirst().ifPresentOrElse(option -> pacer.click(driver, option),
                        () -> field.sendKeys(Keys.TAB));
    }

    // --- delete ---

    @Override
    public void deleteEvent(String eventId, boolean cancelled) throws BitUploadException {
        try {
            delete(eventId, cancelled);
        } catch (BitUploadException | RuntimeException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "delete");
            throw e;
        }
    }

    private void delete(String eventId, boolean cancelled) throws BitUploadException {
        List<Map<String, Object>> events = upcomingEvents();
        int index = indexOf(events, eventId);
        if (index < 0) {
            pacer.pause();
            if (pastEvents().stream().anyMatch(e -> eventId.equals(idOf(e)))) {
                throw new BitUploadException("Bandsintown event " + eventId + " is a past event, which the event "
                        + "list can't remove (its form can).", null, true);
            }
            logger.info("Bandsintown event {} is not listed — already removed", eventId);
            return;
        }
        Map<String, Object> event = events.get(index);
        String city = String.valueOf(event.get("venue_city"));
        int day = Integer.parseInt(String.valueOf(event.get("start_date")).substring(8, 10));
        String[] check = new String[1];
        try {
            wait.until(d -> "ok".equals(check[0] = String.valueOf(js(MARK_ROW, index, city, day, events.size()))));
        } catch (TimeoutException e) {
            throw new BitUploadException("Could not find event " + eventId + " in the Bandsintown list ("
                    + check[0] + ") — nothing was deleted.");
        }

        pacer.pause();
        pacer.click(driver, driver.findElement(By.cssSelector("[data-bz-kebab]")));
        pacer.click(driver, wait.until(d -> firstVisible(By.xpath("//*[normalize-space(text())='Delete']"))));
        confirmDelete(eventId, cancelled);
    }

    @Override
    public void deleteEventInForm(String eventId, boolean cancelled) throws BitUploadException {
        try {
            formDelete(eventId, cancelled);
        } catch (BitUploadException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "form-delete");
            throw e;
        } catch (RuntimeException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "form-delete");
            throw new BitUploadException("Bandsintown's form delete failed: " + e.getMessage(), e);
        }
    }

    /**
     * The event's single-page form (it opens for past events too) → Delete → the same "remove
     * this event?" dialog as the list's. A form that doesn't open for an event neither list
     * shows means it is already gone.
     */
    private void formDelete(String eventId, boolean cancelled) throws BitUploadException {
        driver.get(baseUrl + "/artists/" + artistId + "/events/" + eventId + "?version=single-page");
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.name("venue_name")));
        } catch (TimeoutException e) {
            pacer.pause();
            if (indexOf(upcomingEvents(), eventId) >= 0 || indexOf(pastEvents(), eventId) >= 0) {
                throw new BitUploadException("Bandsintown's form for event " + eventId + " didn't open — nothing was "
                        + "deleted.");
            }
            logger.info("Bandsintown event {} is not listed — already removed", eventId);
            return;
        }
        js(CAPTURE_REPLIES);
        pacer.pause();
        pacer.click(driver, visibleButton("Delete"));
        confirmDelete(eventId, cancelled);
    }

    /**
     * Bandsintown's "remove this event?" dialog, opened from the list's menu or the form: the
     * reason, a detail, then the dialog's own Delete. Everything is looked up inside the dialog
     * — the form behind it has dropdowns, text areas and a Delete button of its own.
     */
    private void confirmDelete(String eventId, boolean cancelled) throws BitUploadException {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("reason")));
        pacer.pause();
        pacer.click(driver, deleteDialog().findElement(By.cssSelector("button[aria-label='Toggle dropdown']")));
        String reason = cancelled ? "CANCELED" : "OTHER";
        pacer.click(driver, wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("li[value='" + reason + "'] button"))));
        if (driver.findElement(By.name("reason")).getDomProperty("value").isBlank()) {
            throw new BitUploadException("Could not pick a reason in Bandsintown's delete dialog — nothing was deleted.");
        }
        pacer.pause();
        pacer.type(driver, deleteDialog().findElement(By.tagName("textarea")),
                cancelled ? "The concert was cancelled." : "Removed by the band.");

        int mark = replyCount();
        pacer.pause();
        pacer.click(driver, deleteDialog().findElement(By.xpath(".//button[normalize-space()='Delete']")));
        Map<String, Object> reply = awaitReply(mark,
                r -> !"GET".equals(r.get("method")) && String.valueOf(r.get("url")).contains("/events/" + eventId),
                "the delete");
        if (!"DELETED".equals(BitResponses.eventStatus(reply))) {
            throw new BitUploadException("Bandsintown did not delete event " + eventId + " — "
                    + BitResponses.describe(reply));
        }
        pacer.pause();
    }

    // --- list (import) ---

    private static final int MAX_PAST_PAGES = 50;

    @Override
    public List<ImportedGig> listEvents() throws BitUploadException {
        try {
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            for (Map<String, Object> event : upcomingEvents()) {
                byId.putIfAbsent(idOf(event), event);
            }
            pacer.pause();
            for (Map<String, Object> event : pastEvents()) {
                byId.putIfAbsent(idOf(event), event);
            }
            List<ImportedGig> gigs = new ArrayList<>();
            byId.values().forEach(event -> BitEventMapper.toImported(event).ifPresent(gigs::add));
            return gigs;
        } catch (BitUploadException | RuntimeException e) {
            SeleniumBitPortalClient.saveScreenshot(driver, "list");
            if (e instanceof BitUploadException bit) {
                throw bit;
            }
            throw new BitUploadException("Could not read the Bandsintown events: " + e.getMessage(), e);
        }
    }

    /**
     * Read-only: what the portal lists for the given event ids, upcoming and past — "status
     * start_date (upcoming|past)", or nothing for an id it doesn't list. For checking by hand.
     */
    Map<String, String> inspect(Collection<String> ids) throws BitUploadException {
        Map<String, String> found = new LinkedHashMap<>();
        for (Map<String, Object> event : upcomingEvents()) {
            if (ids.contains(idOf(event))) {
                found.put(idOf(event), described(event) + " (upcoming)");
            }
        }
        pacer.pause();
        for (Map<String, Object> event : pastEvents()) {
            if (ids.contains(idOf(event))) {
                found.putIfAbsent(idOf(event), described(event) + " (past)");
            }
        }
        return found;
    }

    private static String described(Map<String, Object> event) {
        return event.get("status") + " " + event.get("start_date") + " at " + event.get("venue_name") + ", "
                + event.get("venue_city") + ", " + event.get("venue_country") + " ("
                + event.get("venue_latitude") + ", " + event.get("venue_longitude") + ")";
    }

    /** All past events: the first 20 come with the tab, the rest load as the list is scrolled. */
    private List<Map<String, Object>> pastEvents() throws BitUploadException {
        openEventsTab("past");
        Predicate<Map<String, Object>> pastList = r -> "GET".equals(r.get("method"))
                && String.valueOf(r.get("url")).contains("past=true");
        Map<String, Object> reply = awaitReply(0, pastList, "the past events");
        int mark;
        List<Map<String, Object>> events = new ArrayList<>(BitResponses.events(reply));
        for (int page = 1; hasNext(reply) && page < MAX_PAST_PAGES; page++) {
            mark = replyCount();
            pacer.pause();
            loadMore();
            try {
                reply = awaitReply(mark, pastList, "more past events", Duration.ofSeconds(30));
            } catch (BitUploadException e) {
                logger.warn("Bandsintown: stopped after {} past events — the list did not load more", events.size());
                break;
            }
            events.addAll(BitResponses.events(reply));
        }
        return events;
    }

    private static boolean hasNext(Map<String, Object> reply) {
        Object next = reply.get("next");
        return next != null && !String.valueOf(next).isBlank() && !"null".equals(String.valueOf(next));
    }

    /** Scrolls to the end of the list (and presses "load more" if there is such a button). */
    private void loadMore() {
        js(LOAD_MORE);
        for (WebElement button : driver.findElements(By.xpath(
                "//button[contains(translate(normalize-space(),'LOADMORESHW','loadmoreshw'),'more')]"))) {
            if (button.isDisplayed()) {
                pacer.click(driver, button);
                return;
            }
        }
    }

    private static final String LOAD_MORE = """
            window.scrollTo(0, document.body.scrollHeight);
            for (const el of document.querySelectorAll('*')) {
              if (el.scrollHeight > el.clientHeight + 50 && /(auto|scroll)/.test(getComputedStyle(el).overflowY)) {
                el.scrollTop = el.scrollHeight;
              }
            }""";

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        release.accept(driver);                     // kept open a little for the next session
    }

    // --- shared steps ---

    /** Uploads a CSV through Bulk Upload and returns the portal's reply to it. */
    private Map<String, Object> upload(String csv) throws BitUploadException {
        openEventsTab("upcoming");
        pacer.click(driver, visibleButton("Bulk Upload"));
        WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("input[type=file]")));
        Path file = null;
        try {
            file = Files.createTempFile("bzscraper-bandsintown-", ".csv");
            Files.writeString(file, csv);
            pacer.pause();
            attach(fileInput, file);
            pacer.pause();
            int mark = replyCount();
            pacer.click(driver, visibleButton("Upload"));
            return awaitReply(mark, r -> "PATCH".equals(r.get("method"))
                    && String.valueOf(r.get("url")).contains("status=DRAFT"), "the upload");
        } catch (IOException e) {
            throw new BitUploadException("Could not write the Bandsintown upload file: " + e.getMessage(), e);
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    logger.debug("Could not delete {}", file, e);
                }
            }
        }
    }

    private void attach(WebElement fileInput, Path file) {
        try {
            fileInput.sendKeys(file.toAbsolutePath().toString());
        } catch (ElementNotInteractableException e) {
            js("arguments[0].style.display='block';arguments[0].style.visibility='visible';", fileInput);
            fileInput.sendKeys(file.toAbsolutePath().toString());
        }
    }

    private void clickOk() {
        pacer.pause();
        pacer.click(driver, visibleButton("OK"));
        pacer.pause();
    }

    /** The upcoming events (published ones), as the portal lists them, in display order. */
    private List<Map<String, Object>> upcomingEvents() throws BitUploadException {
        openEventsTab("upcoming");
        Map<String, Object> reply = awaitReply(0, r -> "GET".equals(r.get("method"))
                && String.valueOf(r.get("url")).contains("past=false"), "the event list");
        if (BitResponses.httpStatus(reply) != 200) {
            throw new BitUploadException("Could not read the Bandsintown event list — " + BitResponses.describe(reply));
        }
        return BitResponses.events(reply);
    }

    /**
     * The listed events by id: the upcoming list, plus the past list when the batch has gigs
     * already played (published past events are listed there, not as upcoming).
     */
    private Map<String, Map<String, Object>> listed(List<Gig> batch) throws BitUploadException {
        Map<String, Map<String, Object>> events = new HashMap<>();
        for (Map<String, Object> event : upcomingEvents()) {
            events.put(idOf(event), event);
        }
        ZonedDateTime now = ZonedDateTime.now();
        if (batch.stream().anyMatch(gig -> gig.schedule().showStart().isBefore(now))) {
            pacer.pause();
            for (Map<String, Object> event : pastEvents()) {
                events.putIfAbsent(idOf(event), event);
            }
        }
        return events;
    }

    /** How far from the gig's town Bandsintown may place it before the user is told to check. */
    private static final double PLACE_TOLERANCE_KM = 25;

    /**
     * Bandsintown works out the place from the uploaded text and has picked the wrong one of
     * same-named towns before: compares its coordinates with the town's (when the town was
     * picked from the place search) — a note when they are far apart, else null.
     */
    static String placeCheck(Gig gig, Map<String, Object> event) {
        Address address = gig.location().address();
        if (address == null || !address.resolved()
                || !(event.get("venue_latitude") instanceof Number lat)
                || !(event.get("venue_longitude") instanceof Number lon)) {
            return null;
        }
        double km = address.kilometresTo(lat.doubleValue(), lon.doubleValue());
        return km <= PLACE_TOLERANCE_KM ? null : String.format(Locale.ROOT,
                "Bandsintown placed it %.0f km from %s (%s) — check the place there", km,
                gig.location().city(), address.district());
    }

    private void openEventsTab(String tab) {
        driver.get(baseUrl + "/artists/" + artistId + "/events/" + tab);
        wait.until(d -> firstVisible(By.xpath("//a[normalize-space()='Upcoming Events']")));
        js(CAPTURE_REPLIES);
        pacer.pause();
        for (WebElement close : driver.findElements(By.cssSelector("button[aria-label='Close dialog']"))) {
            if (close.isDisplayed()) {              // promo pop-ups ("Boost your shows")
                pacer.click(driver, close);
                pacer.pause();
            }
        }
    }

    private Map<String, Object> awaitReply(int from, Predicate<Map<String, Object>> match, String what)
            throws BitUploadException {
        return awaitReply(from, match, what, REPLY_WAIT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitReply(int from, Predicate<Map<String, Object>> match, String what,
                                           Duration timeout) throws BitUploadException {
        try {
            return new WebDriverWait(driver, timeout).until(d -> {
                Object replies = js("return (window.__bz || []).slice(arguments[0]);", from);
                if (replies instanceof List<?> list) {
                    for (Object reply : list) {
                        if (reply instanceof Map<?, ?> m && match.test((Map<String, Object>) m)) {
                            return (Map<String, Object>) m;
                        }
                    }
                }
                return null;
            });
        } catch (TimeoutException e) {
            throw new BitUploadException("Bandsintown did not answer " + what + " in time.");
        }
    }

    private int replyCount() {
        Object count = js("return (window.__bz || []).length;");
        return count instanceof Number n ? n.intValue() : 0;
    }

    private WebElement visibleButton(String text) {
        return wait.until(d -> {
            List<WebElement> visible = d.findElements(By.xpath("//button[normalize-space()='" + text + "']"))
                    .stream().filter(WebElement::isDisplayed).toList();
            return visible.isEmpty() ? null : visible.getLast();   // dialogs render last
        });
    }

    /** The delete dialog: the nearest box around its reason field that holds a Delete button (found anew — it re-renders). */
    private WebElement deleteDialog() {
        return driver.findElement(By.xpath("//input[@name='reason']/ancestor::*[.//button[normalize-space()='Delete']][1]"));
    }

    private WebElement firstVisible(By by) {
        return driver.findElements(by).stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
    }

    private static int indexOf(List<Map<String, Object>> events, String eventId) {
        for (int i = 0; i < events.size(); i++) {
            if (eventId.equals(idOf(events.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static String idOf(Map<String, Object> event) {
        Object id = event.get("id");
        return id instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(id);
    }

    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }
}
