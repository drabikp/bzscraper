package sk.drabikp.bzscraper.adapter.out.bandsintown;

import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.adapter.out.browser.BrowserProfile;
import sk.drabikp.bzscraper.adapter.out.browser.WarmBrowser;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Bandsintown client: drives the artist portal (artists.bandsintown.com) in a
 * headless Chromium, because Bandsintown offers artists no write API and its internal
 * API signs every request. Active only when
 * {@code bzscraper.bandsintown.selenium.enabled=true}; credentials come from config.
 *
 * <p>To look like a person rather than a bot: one browser at a time, a persistent
 * browser profile (so a still-valid login is reused instead of logging in with a fresh
 * authenticator code every time), no automation flags or "HeadlessChrome" user agent,
 * and all clicks/typing paced by {@link HumanPacer}.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandsintown.selenium.enabled", havingValue = "true")
public class SeleniumBitPortalClient implements BitPortalClient {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumBitPortalClient.class);
    private static final Duration WAIT = Duration.ofSeconds(25);
    private static final Pattern ARTIST_URL = Pattern.compile("/artists/(\\d+)(/|$)");
    /**
     * Marks the code boxes that are really on screen: the page keeps several code panels
     * side by side and slides the right one into view, so a box counts only if it is the
     * topmost element at its own centre.
     */
    private static final String MARK_VISIBLE_CODE_FORM = """
            document.querySelectorAll('[data-bz-otp]').forEach(e => e.removeAttribute('data-bz-otp'));
            for (const input of document.querySelectorAll('input[autocomplete="one-time-code"]')) {
              const r = input.getBoundingClientRect();
              if (r.width === 0) continue;
              const top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
              if (top !== input && !input.contains(top)) continue;
              const form = input.closest('form');
              input.setAttribute('data-bz-otp', 'code');
              const go = form && [...form.querySelectorAll('button')]
                  .find(b => /continue/i.test(b.innerText) || b.type === 'submit');
              if (go) go.setAttribute('data-bz-otp', 'continue');
              return true;
            }
            return false;""";

    private final String baseUrl;
    private final String userLogin;
    private final String userSecret;
    private final String authenticatorKey;
    private final String configuredArtistId;
    private final String artistName;
    private final boolean headless;
    private final String chromiumBinary;
    private final String chromedriverPath;
    private final String profileDir;
    private final String passwordStore;
    private final HumanPacer pacer;
    /** One browser at a time: a person has one tab open, and the profile can't be shared. */
    private final Semaphore oneBrowser = new Semaphore(1);
    /** The browser stays open a minute after a step, for the next one (one login per run). */
    private final WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofSeconds(60), "bandsintown");

    public SeleniumBitPortalClient(
            @Value("${bzscraper.bandsintown.base-url:https://artists.bandsintown.com}") String baseUrl,
            @Value("${bzscraper.bandsintown.login:}") String userLogin,
            @Value("${bzscraper.bandsintown.password:}") String userSecret,
            @Value("${bzscraper.bandsintown.totp-secret:}") String authenticatorKey,
            @Value("${bzscraper.bandsintown.artist-id:}") String configuredArtistId,
            @Value("${bzscraper.bandsintown.artist-name:Eufory (Band)}") String artistName,
            @Value("${bzscraper.bandsintown.selenium.headless:true}") boolean headless,
            @Value("${bzscraper.bandsintown.selenium.chromium-binary:${bzscraper.bandzone.selenium.chromium-binary:}}")
            String chromiumBinary,
            @Value("${bzscraper.bandsintown.selenium.chromedriver:${bzscraper.bandzone.selenium.chromedriver:}}")
            String chromedriverPath,
            @Value("${bzscraper.bandsintown.selenium.profile-dir:}") String profileDir,
            @Value("${bzscraper.browser.password-store:auto}") String passwordStore,
            @Value("${bzscraper.bandsintown.pacing.min-ms:900}") long minPauseMs,
            @Value("${bzscraper.bandsintown.pacing.max-ms:2600}") long maxPauseMs) {
        this.baseUrl = baseUrl;
        this.userLogin = userLogin;
        this.userSecret = userSecret;
        this.authenticatorKey = authenticatorKey;
        this.configuredArtistId = configuredArtistId;
        this.artistName = artistName;
        this.headless = headless;
        this.chromiumBinary = chromiumBinary;
        this.chromedriverPath = chromedriverPath;
        this.profileDir = profileDir.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".bzscraper", "bandsintown-browser").toString()
                : profileDir;
        this.passwordStore = passwordStore;
        this.pacer = new HumanPacer(minPauseMs, maxPauseMs);
    }

    @Override
    public BitSession openSession() throws BitUploadException {
        if (userLogin.isBlank() || userSecret.isBlank()) {
            throw new BitUploadException("Bandsintown login not configured "
                    + "(bzscraper.bandsintown.login / bzscraper.bandsintown.password).");
        }
        acquireBrowser();
        WebDriver driver = null;
        try {
            driver = warm.take();
            if (driver == null) {
                driver = newDriver();
            }
            WebDriverWait wait = new WebDriverWait(driver, WAIT);
            String artistId = logIn(driver, wait);
            return new SeleniumBitSession(driver, wait, pacer, baseUrl, artistId, artistName, done -> {
                warm.park(done);
                oneBrowser.release();
            });
        } catch (BitUploadException | RuntimeException e) {
            if (driver != null) {
                saveScreenshot(driver, "login");
                driver.quit();
            }
            oneBrowser.release();
            if (e instanceof BitUploadException bit) {
                throw bit;
            }
            throw new BitUploadException("Bandsintown login failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void closeBrowser() {
        warm.close();
    }

    private void acquireBrowser() throws BitUploadException {
        try {
            if (!oneBrowser.tryAcquire(10, TimeUnit.MINUTES)) {
                throw new BitUploadException("Another Bandsintown operation is still running — try again later.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BitUploadException("Interrupted while waiting for the Bandsintown browser.", e);
        }
    }

    private WebDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1366,900",
                "--lang=en-US", "--disable-blink-features=AutomationControlled",
                "--user-data-dir=" + profileDir);
        options.addArguments(BrowserProfile.secretStoreArgs(passwordStore));
        try {
            BrowserProfile.ensurePrivate(Paths.get(profileDir));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the Bandsintown browser profile " + profileDir, e);
        }
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("prefs", Map.of("intl.accept_languages", "en-US,en"));
        if (!chromiumBinary.isBlank()) {
            options.setBinary(chromiumBinary);
        }
        ChromeDriver driver;
        if (!chromedriverPath.isBlank()) {
            ChromeDriverService service = new ChromeDriverService.Builder()
                    .usingDriverExecutable(new File(chromedriverPath))
                    .build();
            driver = new ChromeDriver(service, options);
        } else {
            driver = new ChromeDriver(options);
        }
        // headless Chrome announces itself in the user agent — present a normal one
        String userAgent = String.valueOf(driver.executeScript("return navigator.userAgent;"));
        driver.executeCdpCommand("Network.setUserAgentOverride",
                Map.of("userAgent", userAgent.replace("HeadlessChrome", "Chrome"), "acceptLanguage", "en-US,en"));
        // record the portal's replies from the first request of every page (see SeleniumBitSession)
        driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                Map.of("source", SeleniumBitSession.CAPTURE_REPLIES));
        return driver;
    }

    /** Logs in unless the profile still holds a valid login; returns the artist id. */
    private String logIn(WebDriver driver, WebDriverWait wait) throws BitUploadException {
        driver.get(baseUrl + "/login");
        pacer.pause();                              // a saved login redirects away after a moment
        wait.until(d -> loggedIn(d) || onArtistChooser(d) || !d.findElements(By.id("loginEmail")).isEmpty());
        if (!loggedIn(driver) && !onArtistChooser(driver)) {
            logger.info("Bandsintown: logging in");
            pacer.pause();
            acceptCookies(driver);
            pacer.type(driver, driver.findElement(By.id("loginEmail")), userLogin);
            pacer.pause();
            pacer.type(driver, driver.findElement(By.id("loginPassword")), userSecret);
            pacer.pause();
            pacer.click(driver, driver.findElement(
                    By.xpath("//input[@id='loginPassword']/ancestor::form//button[@type='submit']")));
            try {
                wait.until(d -> loggedIn(d) || codeRequested(d));
            } catch (TimeoutException e) {
                throw new BitUploadException("Bandsintown login failed (check login and password).");
            }
            if (!loggedIn(driver)) {
                enterAuthenticatorCode(driver);
            }
        } else {
            logger.info("Bandsintown: reusing the saved login");
            if (!loggedIn(driver)) {
                chooseArtist(driver);
            }
        }
        return artistId(driver.getCurrentUrl());
    }

    private void enterAuthenticatorCode(WebDriver driver) throws BitUploadException {
        if (authenticatorKey.isBlank()) {
            throw new BitUploadException("Bandsintown asks for an authenticator code, but "
                    + "bzscraper.bandsintown.totp-secret is not set.");
        }
        Totp totp = new Totp(authenticatorKey);
        long now = System.currentTimeMillis() / 1000;
        if (Totp.secondsLeft(now) < 6) {           // don't type a code that expires mid-way
            sleepSeconds(Totp.secondsLeft(now) + 1);
        }
        pacer.pause();
        js(driver, MARK_VISIBLE_CODE_FORM);
        pacer.click(driver, driver.findElement(By.cssSelector("[data-bz-otp='code']")));
        pacer.typeIntoFocused(driver, totp.codeAt(System.currentTimeMillis() / 1000));
        pacer.pause();
        if (!loggedIn(driver)) {                     // the page may submit the code by itself
            try {
                js(driver, MARK_VISIBLE_CODE_FORM);
                List<WebElement> go = driver.findElements(By.cssSelector("[data-bz-otp='continue']"));
                if (!go.isEmpty() && go.getFirst().isEnabled()) {
                    pacer.click(driver, go.getFirst());
                }
            } catch (StaleElementReferenceException e) {
                // the code was already submitted and the page moved on
            }
        }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> loggedIn(d) || onArtistChooser(d));
        } catch (TimeoutException e) {
            throw new BitUploadException("Bandsintown did not accept the authenticator code "
                    + "(check bzscraper.bandsintown.totp-secret and the system clock); stuck at "
                    + driver.getCurrentUrl());
        }
        if (!loggedIn(driver)) {
            chooseArtist(driver);
        }
    }

    /** After a fresh login the portal may show its artist chooser (or a loader) first. */
    private void chooseArtist(WebDriver driver) throws BitUploadException {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> loggedIn(d) || !artistLinks(d).isEmpty());
        } catch (TimeoutException e) {
            throw new BitUploadException("Logged in to Bandsintown, but no artist page appeared (at "
                    + driver.getCurrentUrl() + ").");
        }
        if (loggedIn(driver)) {
            return;
        }
        List<WebElement> links = artistLinks(driver);
        WebElement pick = links.stream()
                .filter(a -> configuredArtistId.isBlank()
                        ? a.getText().contains(artistName)
                        : a.getDomAttribute("href").contains("/artists/" + configuredArtistId + "/"))
                .findFirst()
                .orElse(links.size() == 1 ? links.getFirst() : null);
        if (pick == null) {
            throw new BitUploadException("This Bandsintown account manages several artists — set "
                    + "bzscraper.bandsintown.artist-id (or artist-name) to pick one.");
        }
        pacer.pause();
        pacer.click(driver, pick);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(SeleniumBitPortalClient::loggedIn);
        } catch (TimeoutException e) {
            throw new BitUploadException("Could not open the artist page on Bandsintown.");
        }
    }

    private static List<WebElement> artistLinks(WebDriver driver) {
        return driver.findElements(By.cssSelector("a[href*='/artists/']")).stream()
                .filter(a -> ARTIST_URL.matcher(String.valueOf(a.getDomAttribute("href"))).find())
                .filter(WebElement::isDisplayed)
                .toList();
    }

    private static boolean codeRequested(WebDriver driver) {
        return Boolean.TRUE.equals(js(driver, MARK_VISIBLE_CODE_FORM));
    }

    private static boolean onArtistChooser(WebDriver driver) {
        return driver.getCurrentUrl().contains("/managed-actors");
    }

    private static boolean loggedIn(WebDriver driver) {
        return ARTIST_URL.matcher(driver.getCurrentUrl()).find();
    }

    private String artistId(String url) throws BitUploadException {
        if (!configuredArtistId.isBlank()) {
            return configuredArtistId;
        }
        Matcher m = ARTIST_URL.matcher(url);
        if (!m.find()) {
            throw new BitUploadException("Logged in to Bandsintown but could not tell the artist id — "
                    + "set bzscraper.bandsintown.artist-id.");
        }
        return m.group(1);
    }

    private void acceptCookies(WebDriver driver) {
        for (WebElement button : driver.findElements(By.xpath("//button[normalize-space()='I agree']"))) {
            if (button.isDisplayed()) {
                pacer.click(driver, button);
                pacer.pause();
                return;
            }
        }
    }

    /** Keeps a picture of the page a step failed on (in the temp dir) for diagnosis. */
    static void saveScreenshot(WebDriver driver, String step) {
        try {
            Path target = Paths.get(System.getProperty("java.io.tmpdir"), "bzscraper-bandsintown-" + step + ".png");
            Files.write(target, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
            logger.warn("Bandsintown {} failed at {} — screenshot: {}", step, driver.getCurrentUrl(), target);
        } catch (IOException | RuntimeException e) {
            logger.debug("Could not save a screenshot", e);
        }
    }

    private static Object js(WebDriver driver, String script) {
        return ((JavascriptExecutor) driver).executeScript(script);
    }

    private static void sleepSeconds(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
