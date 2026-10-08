package sk.drabikp.bzscraper.adapter.out.browser;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A platform's one Chromium, shared by every operation on that platform:
 * <ul>
 *   <li>one browser at a time — a profile can't be opened twice, and a person has one tab
 *       open; {@link #acquire} waits for it as long as the caller is willing to;</li>
 *   <li>kept warm a minute after a session ({@link WarmBrowser}) for the next one;</li>
 *   <li>a private profile per account holding the saved login ({@link BrowserProfile});</li>
 *   <li>the same Chromium options for every platform, plus what {@link Customizer} adds;</li>
 *   <li>a screenshot of the page an operation failed on, in the temp directory
 *       ({@code bzscraper-<platform>-<step>.png}).</li>
 * </ul>
 * The platform's client keeps only what is its own: logging in and its pages.
 */
public final class PlatformBrowser {

    private static final Logger logger = LoggerFactory.getLogger(PlatformBrowser.class);
    private static final Duration KEEP_WARM = Duration.ofSeconds(60);

    /** Where the browser is and how it starts; {@code profileDir} is the account's own (see {@link BrowserProfile#dir}). */
    public record Settings(String platform, Path profileDir, boolean headless, String chromiumBinary,
                           String chromedriverPath, String passwordStore, String windowSize) {
    }

    /** What a platform adds to a new browser (options before it starts, set-up after). */
    public interface Customizer {

        default void options(ChromeOptions options) {
        }

        default void started(ChromeDriver driver) {
        }
    }

    /** Work done in the browser that may fail with the platform's own exception. */
    @FunctionalInterface
    public interface Work<T, E extends Exception> {
        T run() throws E;
    }

    private final Settings settings;
    private final Customizer customizer;
    private final Semaphore oneBrowser = new Semaphore(1);
    private final WarmBrowser warm;

    public PlatformBrowser(Settings settings, Customizer customizer) {
        this.settings = settings;
        this.customizer = customizer;
        this.warm = new WarmBrowser(oneBrowser, KEEP_WARM, settings.platform());
    }

    /**
     * The browser — the warm one, or a new one — when it is free within {@code wait}; empty
     * when another operation still has it (or the wait was interrupted). The lease must end
     * with {@link Lease#keepWarm} or {@link Lease#discard}.
     */
    public Optional<Lease> acquire(Duration wait) {
        try {
            if (!oneBrowser.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        try {
            WebDriver driver = warm.take();
            return Optional.of(new Lease(driver != null ? driver : start(), driver != null));
        } catch (RuntimeException e) {
            oneBrowser.release();
            throw e;
        }
    }

    /** Closes the warm browser (app shutdown). */
    public void close() {
        warm.close();
    }

    private ChromeDriver start() {
        try {
            BrowserProfile.ensurePrivate(settings.profileDir());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the " + settings.platform() + " browser profile "
                    + settings.profileDir(), e);
        }
        ChromeOptions options = new ChromeOptions();
        if (settings.headless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=" + settings.windowSize(),
                "--user-data-dir=" + settings.profileDir());
        options.addArguments(BrowserProfile.secretStoreArgs(settings.passwordStore()));
        if (!isBlank(settings.chromiumBinary())) {
            options.setBinary(settings.chromiumBinary());
        }
        customizer.options(options);
        ChromeDriver driver = isBlank(settings.chromedriverPath()) ? new ChromeDriver(options)
                : new ChromeDriver(new ChromeDriverService.Builder()
                        .usingDriverExecutable(new File(settings.chromedriverPath())).build(), options);
        try {
            customizer.started(driver);
        } catch (RuntimeException e) {
            driver.quit();
            throw e;
        }
        return driver;
    }

    /** The options that hide automation from a platform that watches for it (see Bandsintown's pacing). */
    public static void looksLikeAPerson(ChromeOptions options, String language) {
        options.addArguments("--lang=" + language, "--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("prefs", Map.of("intl.accept_languages", language + ",en"));
    }

    /** Headless Chrome names itself in the user agent — present a normal one. */
    public static void normalUserAgent(ChromeDriver driver, String language) {
        String userAgent = String.valueOf(driver.executeScript("return navigator.userAgent;"));
        driver.executeCdpCommand("Network.setUserAgentOverride",
                Map.of("userAgent", userAgent.replace("HeadlessChrome", "Chrome"), "acceptLanguage", language + ",en"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** One operation's hold on the browser. */
    public final class Lease {

        private final WebDriver driver;
        private final boolean wasWarm;
        private boolean ended;

        private Lease(WebDriver driver, boolean wasWarm) {
            this.driver = driver;
            this.wasWarm = wasWarm;
        }

        public WebDriver driver() {
            return driver;
        }

        /** True when it is the warm browser of an earlier session (its login is probably still valid). */
        public boolean wasWarm() {
            return wasWarm;
        }

        /** Done: the browser stays open a little for the next operation. */
        public synchronized void keepWarm() {
            if (!ended) {
                ended = true;
                warm.park(driver);
                oneBrowser.release();
            }
        }

        /** Done, and the browser is not to be trusted (a failed login): closed. */
        public synchronized void discard() {
            if (!ended) {
                ended = true;
                try {
                    driver.quit();
                } catch (RuntimeException e) {
                    logger.debug("Closing the {} browser failed", settings.platform(), e);
                }
                oneBrowser.release();
            }
        }

        /** Keeps a picture of the current page for diagnosis. */
        public void screenshot(String step) {
            try {
                Path target = Paths.get(System.getProperty("java.io.tmpdir"),
                        "bzscraper-" + settings.platform() + "-" + step + ".png");
                Files.write(target, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
                logger.warn("{} {} failed at {} — screenshot: {}", settings.platform(), step,
                        driver.getCurrentUrl(), target);
            } catch (IOException | RuntimeException e) {
                logger.debug("Could not save a screenshot", e);
            }
        }

        /**
         * Runs one operation: any failure leaves a screenshot; an unexpected browser error
         * becomes the platform's failure ({@code asFailure}).
         */
        public <T, E extends Exception> T guarded(String step, Work<T, E> work,
                                                  Function<RuntimeException, E> asFailure) throws E {
            try {
                return work.run();
            } catch (RuntimeException e) {
                screenshot(step);
                throw asFailure.apply(e);
            } catch (Exception e) {
                screenshot(step);
                throw e;
            }
        }
    }
}
