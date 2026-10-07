package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.openqa.selenium.By;
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
import sk.drabikp.bzscraper.domain.model.Gig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * </ul>
 * The portal's own replies are read by wrapping {@code window.fetch} in the page (a
 * hand-made API call would lack the portal's request signature).
 */
final class SeleniumBitSession implements BitSession {

    static final int MAX_ROWS_PER_UPLOAD = 25;
    private static final Logger logger = LoggerFactory.getLogger(SeleniumBitSession.class);
    private static final Duration REPLY_WAIT = Duration.ofSeconds(90);

    private static final String CAPTURE_REPLIES = """
            if (window.__bzHooked) return;
            window.__bzHooked = true;
            window.__bz = [];
            const original = window.fetch;
            window.fetch = function (u, o) {
              const url = String(u && u.url || u);
              const method = String((o && o.method) || (u && u.method) || 'GET').toUpperCase();
              const reply = original.apply(this, arguments);
              if (/\\/managed-actors\\/\\d+\\/events/.test(url)) {
                reply.then(r => r.clone().text().then(t => {
                  let json = null;
                  try { json = JSON.parse(t); } catch (e) {}
                  window.__bz.push({method, url, status: r.status, json, text: t.slice(0, 2000)});
                })).catch(e => window.__bz.push({method, url, status: -1, json: null, text: String(e)}));
              }
              return reply;
            };""";

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
    private final Runnable release;
    private boolean closed;

    SeleniumBitSession(WebDriver driver, WebDriverWait wait, HumanPacer pacer, String baseUrl,
                       String artistId, String artistName, Runnable release) {
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
            throw new BitUploadException("Bandsintown rejected the upload — " + BitResponses.describe(reply));
        }
        String[] drafts = BitResponses.draftIds(reply, batch.size());
        String[] existing = BitResponses.updatedIds(reply, batch.size());
        if (Arrays.stream(drafts).anyMatch(Objects::nonNull)) {
            publishUploadedDrafts(notifyFollowers);
        } else {
            clickOk();                              // every row matched an existing event
        }

        Map<String, String> statuses = upcomingStatuses();
        List<Created> results = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            Gig gig = batch.get(i);
            String id = drafts[i] != null ? drafts[i] : existing[i];
            if (id == null) {
                results.add(Created.failed(gig, "Bandsintown rejected this row — " + BitResponses.describe(reply)));
            } else if ("PUBLISHED".equals(statuses.get(id))) {
                results.add(Created.published(gig, id));
            } else {
                results.add(Created.failed(gig, "Uploaded to Bandsintown as event " + id + ", but it is not "
                        + "published (" + statuses.getOrDefault(id, "not listed") + ") — publish or delete it there."));
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
        if (!BitResponses.ok(reply) || !eventId.equals(updated)) {
            throw new BitUploadException("Bandsintown did not update event " + eventId + " — "
                    + BitResponses.describe(reply));
        }
        clickOk();
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
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("reason")));
        pacer.pause();
        pacer.click(driver, driver.findElement(By.cssSelector("button[aria-label='Toggle dropdown']")));
        String reason = cancelled ? "CANCELED" : "OTHER";
        pacer.click(driver, wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("li[value='" + reason + "'] button"))));
        if (driver.findElement(By.name("reason")).getDomProperty("value").isBlank()) {
            throw new BitUploadException("Could not pick a reason in Bandsintown's delete dialog — nothing was deleted.");
        }
        pacer.pause();
        pacer.type(driver, firstVisible(By.tagName("textarea")),
                cancelled ? "The concert was cancelled." : "Removed by the band.");

        int mark = replyCount();
        pacer.pause();
        pacer.click(driver, driver.findElement(By.xpath(
                "//textarea/ancestor::*[.//button[normalize-space()='Delete']][1]//button[normalize-space()='Delete']")));
        Map<String, Object> reply = awaitReply(mark,
                r -> !"GET".equals(r.get("method")) && String.valueOf(r.get("url")).contains("/events/" + eventId),
                "the delete");
        if (!"DELETED".equals(BitResponses.eventStatus(reply))) {
            throw new BitUploadException("Bandsintown did not delete event " + eventId + " — "
                    + BitResponses.describe(reply));
        }
        pacer.pause();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            driver.quit();
        } catch (RuntimeException e) {
            logger.debug("Closing the Bandsintown browser failed", e);
        } finally {
            release.run();
        }
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
        openEventsTab("past");
        int mark = replyCount();
        pacer.click(driver, firstVisible(By.xpath("//a[normalize-space()='Upcoming Events']")));
        Map<String, Object> reply = awaitReply(mark, r -> "GET".equals(r.get("method"))
                && String.valueOf(r.get("url")).contains("past=false"), "the event list");
        if (BitResponses.httpStatus(reply) != 200) {
            throw new BitUploadException("Could not read the Bandsintown event list — " + BitResponses.describe(reply));
        }
        return BitResponses.events(reply);
    }

    private Map<String, String> upcomingStatuses() throws BitUploadException {
        Map<String, String> statuses = new HashMap<>();
        for (Map<String, Object> event : upcomingEvents()) {
            statuses.put(idOf(event), String.valueOf(event.get("status")));
        }
        return statuses;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitReply(int from, Predicate<Map<String, Object>> match, String what)
            throws BitUploadException {
        try {
            return new WebDriverWait(driver, REPLY_WAIT).until(d -> {
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
