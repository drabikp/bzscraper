package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.adapter.out.browser.BrowserProfile;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.domain.model.EntryType;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Location;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Bandzone publisher: opens ONE headless-browser session per batch (single
 * login) and creates each gig through the band-admin 2-step wizard, edits it through
 * the concert's update form (details, venue, poster) and performing-bands tab (lineup),
 * and cancels/deletes it through the update delete tab. Active only
 * when {@code bzscraper.bandzone.selenium.enabled=true} (otherwise the stub is
 * used, so the app boots without a browser). Credentials come from config.
 *
 * The browser keeps a saved login in a private, per-account profile (see
 * {@link BrowserProfile}): the password is used only when Bandzone ended that login.
 * One browser at a time — a profile can't be opened twice.
 *
 * Field values are set via JavaScript (with input/change events) rather than
 * sendKeys, because Bandzone's date/city inputs are JS-wrapped and not directly
 * interactable — the approach that validated the flow manually.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandzone.selenium.enabled", havingValue = "true")
public class SeleniumBandzonePortalClient implements BandzonePortalClient {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumBandzonePortalClient.class);
    private static final Duration WAIT = Duration.ofSeconds(20);

    private final String baseUrl;
    private final String userLogin;
    private final String userSecret;
    private final String bandSlug;
    private final boolean headless;
    private final String chromiumBinary;
    private final String chromedriverPath;
    private final Path profileDir;
    private final String passwordStore;
    private final Semaphore oneBrowser = new Semaphore(1);

    public SeleniumBandzonePortalClient(
            @Value("${bzscraper.bandzone.base-url:https://bandzone.cz}") String baseUrl,
            @Value("${bzscraper.bandzone.login:}") String userLogin,
            @Value("${bzscraper.bandzone.password:}") String userSecret,
            @Value("${bzscraper.bandzone.band-slug:}") String bandSlug,
            @Value("${bzscraper.bandzone.selenium.headless:true}") boolean headless,
            @Value("${bzscraper.bandzone.selenium.chromium-binary:}") String chromiumBinary,
            @Value("${bzscraper.bandzone.selenium.chromedriver:}") String chromedriverPath,
            @Value("${bzscraper.bandzone.selenium.profile-dir:}") String profileDir,
            @Value("${bzscraper.browser.password-store:auto}") String passwordStore) {
        this.baseUrl = baseUrl;
        this.userLogin = userLogin;
        this.userSecret = userSecret;
        this.bandSlug = bandSlug;
        this.headless = headless;
        this.chromiumBinary = chromiumBinary;
        this.chromedriverPath = chromedriverPath;
        this.profileDir = BrowserProfile.dir(profileDir, "bandzone", userLogin);
        this.passwordStore = passwordStore;
    }

    @Override
    public BandzoneSession openSession() throws BandzoneUploadException {
        if (userLogin.isBlank() || userSecret.isBlank() || bandSlug.isBlank()) {
            throw new BandzoneUploadException("Bandzone credentials/band-slug not configured "
                    + "(bzscraper.bandzone.login/password/band-slug).");
        }
        acquireBrowser();
        WebDriver driver = null;
        try {
            driver = newDriver();
            WebDriverWait wait = new WebDriverWait(driver, WAIT);
            if (loggedIn(driver)) {
                logger.info("Bandzone: reusing the saved login");
            } else {
                logIn(driver, wait);
            }
            dismissCookies(driver);
            return new SeleniumBandzoneSession(driver, wait, baseUrl, bandSlug, oneBrowser::release);
        } catch (BandzoneUploadException | RuntimeException e) {
            if (driver != null) {
                driver.quit();
            }
            oneBrowser.release();
            if (e instanceof BandzoneUploadException bz) {
                throw bz;
            }
            throw new BandzoneUploadException("Bandzone login failed: " + e.getMessage(), e);
        }
    }

    private void acquireBrowser() throws BandzoneUploadException {
        try {
            if (!oneBrowser.tryAcquire(10, TimeUnit.MINUTES)) {
                throw new BandzoneUploadException("Another Bandzone operation is still running — try again later.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BandzoneUploadException("Interrupted while waiting for the Bandzone browser.", e);
        }
    }

    /** A still-valid saved login shows the logout link on any page. */
    private boolean loggedIn(WebDriver driver) {
        driver.get(baseUrl + "/");
        return !driver.findElements(By.cssSelector("a[href*='logout']")).isEmpty();
    }

    private WebDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        try {
            BrowserProfile.ensurePrivate(profileDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the Bandzone browser profile " + profileDir, e);
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1280,1024",
                "--user-data-dir=" + profileDir);
        options.addArguments(BrowserProfile.secretStoreArgs(passwordStore));
        if (!chromiumBinary.isBlank()) {
            options.setBinary(chromiumBinary);
        }
        if (!chromedriverPath.isBlank()) {
            ChromeDriverService service = new ChromeDriverService.Builder()
                    .usingDriverExecutable(new File(chromedriverPath))
                    .build();
            return new ChromeDriver(service, options);
        }
        return new ChromeDriver(options);
    }

    private void logIn(WebDriver driver, WebDriverWait wait) throws BandzoneUploadException {
        driver.get(baseUrl + "/login.html?do=login");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("frmloginForm-login")));
        setValue(driver, "#frmloginForm-login", userLogin);
        setValue(driver, "#frmloginForm-password", userSecret);
        driver.findElement(By.id("frmloginForm-doLogin")).click();
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[href*='logout']")));
        } catch (RuntimeException e) {
            throw new BandzoneUploadException("Bandzone login failed (check credentials).");
        }
    }

    private static void dismissCookies(WebDriver driver) {
        List<WebElement> consent = driver.findElements(By.xpath(
                "//a[contains(@href,'approveCookies')] | //button[contains(.,'pořádku')]"));
        if (!consent.isEmpty()) {
            try {
                jsClick(driver, consent.get(0));
            } catch (RuntimeException ignored) {
                // best-effort; the JS field-setters do not require the banner gone
            }
        }
    }

    /** One authenticated browser session; creates many gigs without re-login. */
    static final class SeleniumBandzoneSession implements BandzoneSession {

        private static final DateTimeFormatter BZ_DATE = DateTimeFormatter.ofPattern("d.M.yyyy");
        private static final DateTimeFormatter BZ_TIME = DateTimeFormatter.ofPattern("HH:mm");
        /** After the wizard's last step Bandzone redirects to {@code /koncert/<id>-<slug>}. */
        private static final Pattern CONCERT_URL = Pattern.compile("/koncert/(\\d+)");

        private static final Duration SUGGESTION_WAIT = Duration.ofSeconds(5);
        private static final Duration UPLOAD_WAIT = Duration.ofSeconds(60);
        private static final Duration RELOAD_WAIT = Duration.ofSeconds(5);

        private final WebDriver driver;
        private final WebDriverWait wait;
        private final String baseUrl;
        private final String bandSlug;
        private final Runnable onClose;
        private String ownBandName; // read once per session from the band's profile

        SeleniumBandzoneSession(WebDriver driver, WebDriverWait wait, String baseUrl, String bandSlug,
                                Runnable onClose) {
            this.driver = driver;
            this.wait = wait;
            this.baseUrl = baseUrl;
            this.bandSlug = bandSlug;
            this.onClose = onClose;
        }

        @Override
        public String createGig(Gig gig) throws BandzoneUploadException {
            try {
                openWizard();
                fillDateAndCity(gig);
                passDuplicateScreen();
                fillInfo(gig);
                return sendAndGetCreatedId(gig);
            } catch (BandzoneUploadException e) {
                throw e;
            } catch (RuntimeException e) {
                // Fail only this gig; the session stays open for the rest of the batch.
                throw new BandzoneUploadException("Bandzone create failed for '" + gig.title()
                        + "': " + e.getMessage(), e);
            }
        }

        @Override
        public void updateGig(String bandzoneId, Gig gig) throws BandzoneUploadException {
            String updateUrl = baseUrl + "/koncert/" + bandzoneId + "/update?updateTabs-at=updateForm";
            try {
                driver.get(updateUrl);
                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("frmupdateForm-send")));
                // the upload re-renders the form, so it goes before the fields are set
                if (gig.posterImageUrl() != null) {
                    uploadPoster(gig.posterImageUrl());
                }
                // Autocomplete picks reload the form via AJAX, so they go before the plain
                // fields; the venue search only offers clubs of the selected city.
                selectCity(gig.location());
                selectVenue(gig.location().venue());
                // the update form uses the same field names as the create wizard
                setValue(driver, "[name='start[date]']", gig.schedule().start().format(BZ_DATE));
                setValue(driver, "[name='start[time]']", gig.schedule().start().format(BZ_TIME));
                setValue(driver, "[name='end[date]']", gig.schedule().hasEnd() ? gig.schedule().end().format(BZ_DATE) : "");
                setValue(driver, "[name='end[time]']", gig.schedule().hasEnd() ? gig.schedule().end().format(BZ_TIME) : "");
                fillInfo(gig);

                WebElement send = driver.findElement(By.id("frmupdateForm-send"));
                jsClick(driver, send);
                wait.until(ExpectedConditions.stalenessOf(send));
                verifyStored(updateUrl, bandzoneId, gig);

                new BandzoneLineupPage(driver, wait, baseUrl, bandzoneId).sync(gig.lineup(), ownBandName());
                logger.info("Bandzone gig updated: '{}' (id {})", gig.title(), bandzoneId);
            } catch (BandzoneUploadException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new BandzoneUploadException("Bandzone update failed for concert " + bandzoneId
                        + ": " + e.getMessage(), e);
            }
        }

        /**
         * A rejected form re-renders with the submitted values, so re-read the stored
         * concert and compare the fields that identify the gig.
         */
        private void verifyStored(String updateUrl, String bandzoneId, Gig gig) throws BandzoneUploadException {
            driver.get(updateUrl);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("frmupdateForm-send")));
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("name", gig.title());
            expected.put("start[date]", gig.schedule().start().format(BZ_DATE));
            expected.put("start[time]", gig.schedule().start().format(BZ_TIME));
            expected.put("end[date]", gig.schedule().hasEnd() ? gig.schedule().end().format(BZ_DATE) : "");
            expected.put("cityId__container[textInput]", gig.location().city());
            expected.put("venueId__container[textInput]", gig.location().venue() == null ? "" : gig.location().venue());
            expected.put("entry", gig.admission().isPaid() ? gig.admission().amount() : "");
            expected.put("info", gig.description() == null ? "" : gig.description());
            List<String> mismatched = new ArrayList<>();
            expected.forEach((field, value) -> {
                String stored = driver.findElement(By.name(field)).getAttribute("value");
                if (!value.equalsIgnoreCase(stored == null ? "" : stored.trim())) {
                    mismatched.add(field + "='" + stored + "' (expected '" + value + "')");
                }
            });
            String storedType = (String) ((JavascriptExecutor) driver).executeScript(
                    "var r=document.querySelector('[name=entryType]:checked');return r?r.value:'';");
            if (!entryTypeValue(gig.admission().type()).equals(storedType)) {
                mismatched.add("entryType='" + storedType + "' (expected '"
                        + entryTypeValue(gig.admission().type()) + "')");
            }
            if (!mismatched.isEmpty()) {
                throw new BandzoneUploadException("Bandzone did not save the changes to concert "
                        + bandzoneId + ": " + String.join(", ", mismatched));
            }
        }

        @Override
        public void cancelGig(String bandzoneId) throws BandzoneUploadException {
            submitDeleteForm(bandzoneId, "cancelGig");
        }

        @Override
        public void deleteGig(String bandzoneId) throws BandzoneUploadException {
            submitDeleteForm(bandzoneId, "delete");
        }

        private void submitDeleteForm(String bandzoneId, String buttonName) throws BandzoneUploadException {
            try {
                driver.get(baseUrl + "/koncert/" + bandzoneId + "/update?updateTabs-at=profileDeleteForm");
                wait.until(ExpectedConditions.presenceOfElementLocated(By.name(buttonName)));
                // requestSubmit with the button's value posts the delete/cancel while
                // bypassing the JS confirm() dialog that a plain click would trigger.
                ((JavascriptExecutor) driver).executeScript(
                        "var n=arguments[0];"
                                + "var b=document.querySelector('[name=\"'+n+'\"]'); var f=b?b.form:null;"
                                + "if(f){ if(b.value){var h=document.createElement('input');h.type='hidden';"
                                + "h.name=n;h.value=b.value;f.appendChild(h);}"
                                + " if(f.requestSubmit){f.requestSubmit(b);}else{f.submit();} }",
                        buttonName);
                wait.until(ExpectedConditions.urlContains("bandzone.cz"));
            } catch (RuntimeException e) {
                throw new BandzoneUploadException("Bandzone " + buttonName + " failed for concert "
                        + bandzoneId + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            try {
                driver.quit();
            } finally {
                onClose.run();
            }
        }

        private void openWizard() {
            driver.get(baseUrl + "/koncert/zalozit.html?initBandPublicId=" + bandSlug);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.name("start[date]")));
        }

        private void fillDateAndCity(Gig gig) throws BandzoneUploadException {
            setValue(driver, "[name='start[date]']", gig.schedule().start().format(BZ_DATE));
            setValue(driver, "[name='start[time]']", gig.schedule().start().format(BZ_TIME));
            selectCity(gig.location());
            jsClickByName(driver, "continue");
        }

        /**
         * Picks the town through the city search: the suggestion that fits by name, country
         * and district ({@link BandzoneTowns}); several fits or none → a clear error for the
         * user, never a guess. Then waits for {@code cityId} to take the picked town.
         */
        private void selectCity(Location location) throws BandzoneUploadException {
            String city = location.city();
            String currentText = driver.findElement(By.name("cityId__container[textInput]")).getAttribute("value");
            String previousId = driver.findElement(By.name("cityId")).getAttribute("value");
            // The form shows only the town's name, and a name may be the wrong town of several
            // (once: a Czech village for Košice) — a town picked from the place search is
            // therefore chosen again every time; only a merely typed one is kept as it is.
            if (location.district() == null && city.equalsIgnoreCase(currentText)
                    && previousId != null && !previousId.isBlank()) {
                return;
            }
            setValue(driver, "[name='cityId__container[textInput]']", city);
            jsClickByName(driver, "cityId__container[searchButton]");

            List<BandzoneTowns.Suggestion> suggestions;
            try {
                suggestions = wait.until(d -> townSuggestions());
            } catch (TimeoutException e) {
                throw new BandzoneUploadException("Bandzone's city search gave nothing for '" + city
                        + "' — correct the gig's city (the town's name, not a district) and try again.", e, true);
            }
            BandzoneTowns.Choice choice = BandzoneTowns.choose(city, location.country(), location.district(),
                    suggestions);
            if (choice.pick() == null) {
                throw new BandzoneUploadException(choice.problem(), null, true);
            }
            WebElement cityIdInput = driver.findElement(By.name("cityId"));
            jsClick(driver, driver.findElement(By.cssSelector("li[data-bz-town='" + choice.pick().index() + "']")));
            awaitFormReload(cityIdInput);
            wait.until(d -> {
                String v = d.findElement(By.name("cityId")).getAttribute("value");
                return v != null && !v.isBlank();
            });
        }

        /**
         * The city search's suggestions (name + "okres …, kraj …[, Slovensko]"), each marked
         * {@code data-bz-town=<index>} to click it; null while none are shown. The list's last
         * entry offers to add a new town — never used.
         */
        @SuppressWarnings("unchecked")
        private List<BandzoneTowns.Suggestion> townSuggestions() {
            List<List<Object>> rows = (List<List<Object>>) ((JavascriptExecutor) driver).executeScript(
                    "var items = Array.from(document.querySelectorAll('li')).filter(li => li.querySelector('.name'));"
                            + "var adds = Array.from(document.querySelectorAll('li')).some(li => /přidat nové město/i.test(li.innerText));"
                            + "if (!items.length && !adds) return null;"
                            + "return items.map((li, i) => { li.setAttribute('data-bz-town', i);"
                            + " var d = li.querySelector('.description');"
                            + " return [li.querySelector('.name').innerText.trim(), d ? d.innerText.trim() : '']; });");
            if (rows == null) {
                return null;
            }
            List<BandzoneTowns.Suggestion> suggestions = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                suggestions.add(new BandzoneTowns.Suggestion(i, String.valueOf(rows.get(i).get(0)),
                        String.valueOf(rows.get(i).get(1))));
            }
            return suggestions;
        }

        /**
         * Sets the venue. An exact (case-insensitive) match in Bandzone's club database
         * links the club; anything else is kept as free text, which Bandzone accepts.
         * A gig without a venue clears it.
         */
        private void selectVenue(String venue) {
            String currentText = driver.findElement(By.name("venueId__container[textInput]")).getAttribute("value");
            if (venue != null && venue.equalsIgnoreCase(currentText)) {
                return;
            }
            setValue(driver, "[name='venueId']", "");
            if (venue == null) {
                setValue(driver, "[name='venueId__container[textInput]']", "");
                return;
            }
            ((JavascriptExecutor) driver).executeScript(
                    "document.querySelectorAll('ul.ui-autocomplete').forEach(u=>{u.innerHTML='';u.style.display='none';});"
                            + "var t=document.querySelector('[name=\"venueId__container[textInput]\"]');"
                            + "t.focus();t.value=arguments[0];t.dispatchEvent(new Event('input',{bubbles:true}));",
                    venue);
            try {
                new WebDriverWait(driver, SUGGESTION_WAIT).until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                        "return Array.from(document.querySelectorAll('ul.ui-autocomplete'))"
                                + ".some(u=>u.offsetParent!==null&&u.querySelector('li'));"));
            } catch (TimeoutException e) {
                keepFreeTextVenue(venue); // no club suggestions
                return;
            }
            WebElement venueIdInput = driver.findElement(By.name("venueId"));
            Boolean linked = (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "var n=arguments[0].toLowerCase();"
                            + "var m=Array.from(document.querySelectorAll('ul.ui-autocomplete li'))"
                            + ".filter(l=>l.offsetParent!==null)"
                            + ".find(l=>{var h=l.querySelector('h4.title');return h&&h.innerText.trim().toLowerCase()===n;});"
                            + "if(m){(m.querySelector('a')||m).click();return true;} return false;",
                    venue);
            if (Boolean.TRUE.equals(linked)) {
                awaitFormReload(venueIdInput);
                wait.until(d -> {
                    String v = d.findElement(By.name("venueId")).getAttribute("value");
                    return v != null && !v.isBlank();
                });
            } else {
                keepFreeTextVenue(venue);
            }
        }

        /**
         * Picking from a city/venue autocomplete re-renders the update form via AJAX
         * about a second later, re-posting its current values; anything typed before
         * that lands is lost. Waits for the old form to go (the create wizard does not
         * reload, so a timeout just means there was nothing to wait for).
         */
        private void awaitFormReload(WebElement oldFormElement) {
            try {
                new WebDriverWait(driver, RELOAD_WAIT).until(ExpectedConditions.stalenessOf(oldFormElement));
            } catch (TimeoutException e) {
                // no reload
            }
        }

        /**
         * Closing the club menu without a pick makes the widget restore the previously
         * linked club's name, so the free text is written again — without events — and
         * the club link cleared.
         */
        private void keepFreeTextVenue(String venue) {
            ((JavascriptExecutor) driver).executeScript(
                    "document.querySelectorAll('ul.ui-autocomplete').forEach(u=>u.style.display='none');"
                            + "var t=document.querySelector('[name=\"venueId__container[textInput]\"]');"
                            + "t.blur();t.value=arguments[0];"
                            + "document.querySelector('[name=\"venueId\"]').value='';",
                    venue);
        }

        /**
         * Downloads the poster and uploads it through the form's auto-uploading file
         * input (Bandzone keeps one image; a new upload replaces it). Bandzone offers no
         * way to remove a poster, so a gig without one leaves the current image alone.
         */
        private void uploadPoster(String posterUrl) throws BandzoneUploadException {
            Path file = downloadPoster(posterUrl);
            try {
                By input = By.name("profileImage[uploader]");
                wait.until(d -> d.findElement(input).isEnabled()); // enabled once the uploader's JS is ready
                WebElement formBefore = driver.findElement(By.id("frmupdateForm-send"));
                driver.findElement(input).sendKeys(file.toAbsolutePath().toString());
                new WebDriverWait(driver, UPLOAD_WAIT).until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                        "var c=document.querySelector('[id^=files-container]');"
                                + "return !!c && /Nahráno/.test(c.innerText) && !c.querySelector('.template-upload');"));
                awaitFormReload(formBefore); // the uploader reloads the form when done
                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("frmupdateForm-send")));
            } catch (TimeoutException e) {
                throw new BandzoneUploadException("Poster upload did not finish: " + posterUrl, e);
            } finally {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // temp file; the OS cleans it up eventually
                }
            }
        }

        private static Path downloadPoster(String posterUrl) throws BandzoneUploadException {
            try {
                String path = URI.create(posterUrl).getPath();
                String ext = path != null && path.matches(".*\\.(?i)(jpe?g|png|gif|webp)$")
                        ? path.substring(path.lastIndexOf('.')) : ".jpg";
                Path file = Files.createTempFile("bz-poster-", ext);
                HttpResponse<Path> response = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL).build()
                        .send(HttpRequest.newBuilder(URI.create(posterUrl)).GET().build(),
                                HttpResponse.BodyHandlers.ofFile(file));
                if (response.statusCode() != 200) {
                    Files.deleteIfExists(file);
                    throw new BandzoneUploadException("Poster download failed (HTTP "
                            + response.statusCode() + "): " + posterUrl);
                }
                return file;
            } catch (IOException | IllegalArgumentException e) {
                throw new BandzoneUploadException("Poster download failed: " + posterUrl, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BandzoneUploadException("Poster download interrupted: " + posterUrl, e);
            }
        }

        /**
         * The publishing band's display name, as it appears in a concert's performer list.
         * Read from {@code og:title}: the profile's {@code <h1>} also holds genre and city.
         */
        private String ownBandName() throws BandzoneUploadException {
            if (ownBandName == null) {
                driver.get(baseUrl + "/" + bandSlug);
                String name = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("meta[property='og:title']"))).getAttribute("content");
                if (name == null || name.isBlank()) {
                    throw new BandzoneUploadException("Could not read the band name of '" + bandSlug + "'.");
                }
                ownBandName = name.trim();
            }
            return ownBandName;
        }

        /**
         * Between the wizard's two steps Bandzone may list similar concerts (same date and
         * city) and ask whether this is one of them; "new" continues to the info step.
         */
        private void passDuplicateScreen() {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.name("new")),
                    ExpectedConditions.presenceOfElementLocated(By.name("name"))));
            if (!driver.findElements(By.name("new")).isEmpty()) {
                jsClickByName(driver, "new");
                wait.until(ExpectedConditions.presenceOfElementLocated(By.name("name")));
            }
        }

        /** Sets every info field, blanking the optional ones the gig does not have (for edits). */
        private void fillInfo(Gig gig) {
            setValue(driver, "[name='name']", gig.title());
            selectEntryType(gig.admission().type());
            setValue(driver, "[name='entry']", gig.admission().isPaid() ? gig.admission().amount() : "");
            setValue(driver, "[name='info']", orEmpty(gig.description()));
            setValue(driver, "[name='facebookUrl']", orEmpty(gig.facebookUrl()));
        }

        private static String orEmpty(String value) {
            return value == null ? "" : value;
        }

        /** The {@code entryType} radio value Bandzone stores (0 = paid, 1 = voluntary, 2 = free). */
        private static String entryTypeValue(EntryType entryType) {
            return switch (entryType == null ? EntryType.FREE : entryType) {
                case PAID -> "0";
                case VOLUNTARY -> "1";
                case FREE -> "2";
            };
        }

        private void selectEntryType(EntryType entryType) {
            String term = switch (entryType == null ? EntryType.FREE : entryType) {
                case FREE -> "zdarma";
                case VOLUNTARY -> "dobrovoln";
                case PAID -> "ručně";
            };
            ((JavascriptExecutor) driver).executeScript(
                    "var term=arguments[0];"
                            + "var r=Array.from(document.querySelectorAll('[name=\"entryType\"]'))"
                            + ".find(x=>{var l=x.closest('label')||document.querySelector('label[for=\"'+x.id+'\"]');"
                            + "return l&&l.innerText.toLowerCase().includes(term)}); if(r){r.click();}",
                    term);
        }

        /**
         * Submits the wizard and reads the new concert id from the page Bandzone redirects
         * to. (Searching the band page by title is unreliable: same-titled gigs and the
         * notification panel link to other concerts.)
         */
        private String sendAndGetCreatedId(Gig gig) throws BandzoneUploadException {
            jsClick(driver, driver.findElement(By.name("send")));
            try {
                wait.until(d -> CONCERT_URL.matcher(d.getCurrentUrl()).find());
            } catch (RuntimeException e) {
                throw new BandzoneUploadException("Gig '" + gig.title() + "' was submitted but Bandzone "
                        + "did not open the created concert (still at " + driver.getCurrentUrl() + ").", e);
            }
            Matcher m = CONCERT_URL.matcher(driver.getCurrentUrl());
            m.find();
            String id = m.group(1);
            logger.info("Bandzone gig created: '{}' (id {})", gig.title(), id);
            return id;
        }
    }

    static void setValue(WebDriver driver, String cssSelector, String value) {
        ((JavascriptExecutor) driver).executeScript(
                "var e=document.querySelector(arguments[0]);"
                        + "if(e){e.value=arguments[1];"
                        + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "e.dispatchEvent(new Event('change',{bubbles:true}));}",
                cssSelector, value);
    }

    static void jsClickByName(WebDriver driver, String name) {
        ((JavascriptExecutor) driver).executeScript(
                "var e=document.querySelector(arguments[0]); if(e){e.click();}",
                "[name='" + name + "']");
    }

    private static void jsClick(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }
}
