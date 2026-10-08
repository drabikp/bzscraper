package sk.drabikp.bzscraper.adapter.out.bandzone;

import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.adapter.out.browser.BrowserProperties;
import sk.drabikp.bzscraper.adapter.out.browser.PlatformBrowser;

import java.time.Duration;
import java.util.List;

/**
 * Real Bandzone publisher: opens ONE headless-browser session per batch (single
 * login) and creates each gig through the band-admin 2-step wizard, edits it through
 * the concert's update form (details, venue, poster) and performing-bands tab (lineup),
 * and cancels/deletes it through the update delete tab. Active only
 * when {@code bzscraper.bandzone.selenium.enabled=true} (otherwise the stub is
 * used, so the app boots without a browser). Credentials come from {@link BandzoneProperties}.
 *
 * The browser ({@link PlatformBrowser}: one at a time, kept warm, a private per-account
 * profile) keeps a saved login: the password is used only when Bandzone ended that login.
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
    private final PlatformBrowser browser;

    public SeleniumBandzonePortalClient(BandzoneProperties properties, BrowserProperties browserProperties) {
        properties.requireLogin();
        this.baseUrl = properties.baseUrl();
        this.userLogin = properties.login();
        this.userSecret = properties.password();
        this.bandSlug = properties.bandSlug();
        this.browser = new PlatformBrowser(properties.selenium().browser("bandzone", userLogin, browserProperties,
                "1280,1024"), new PlatformBrowser.Customizer() {
        });
    }

    @Override
    public BandzoneSession openSession(Duration waitForBrowser) throws BandzoneUploadException {
        PlatformBrowser.Lease lease = browser.acquire(waitForBrowser).orElseThrow(() ->
                BandzoneUploadException.busy("Another Bandzone operation is using the browser."));
        try {
            WebDriver driver = lease.driver();
            WebDriverWait wait = new WebDriverWait(driver, WAIT);
            if (loggedIn(driver)) {
                logger.info("Bandzone: reusing the saved login");
            } else {
                logIn(driver, wait);
            }
            dismissCookies(driver);
            return new SeleniumBandzoneSession(lease, wait, baseUrl, bandSlug);
        } catch (BandzoneUploadException | RuntimeException e) {
            lease.screenshot("login");
            lease.discard();
            if (e instanceof BandzoneUploadException bz) {
                throw bz;
            }
            throw new BandzoneUploadException("Bandzone login failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void closeBrowser() {
        browser.close();
    }

    /** A still-valid saved login shows the logout link on any page. */
    private boolean loggedIn(WebDriver driver) {
        driver.get(baseUrl + "/");
        return !driver.findElements(By.cssSelector("a[href*='logout']")).isEmpty();
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

    private static void setValue(WebDriver driver, String cssSelector, String value) {
        ((JavascriptExecutor) driver).executeScript(
                "var e=document.querySelector(arguments[0]);"
                        + "if(e){e.value=arguments[1];"
                        + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "e.dispatchEvent(new Event('change',{bubbles:true}));}",
                cssSelector, value);
    }

    private static void jsClick(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }
}
