package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.domain.model.EntryType;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.io.File;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Real Bandzone publisher: opens ONE headless-browser session per batch (single
 * login) and creates each gig through the band-admin 3-step wizard. Active only
 * when {@code bzscraper.bandzone.selenium.enabled=true} (otherwise the stub is
 * used, so the app boots without a browser). Credentials come from config.
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

    public SeleniumBandzonePortalClient(
            @Value("${bzscraper.bandzone.base-url:https://bandzone.cz}") String baseUrl,
            @Value("${bzscraper.bandzone.login:}") String userLogin,
            @Value("${bzscraper.bandzone.password:}") String userSecret,
            @Value("${bzscraper.bandzone.band-slug:}") String bandSlug,
            @Value("${bzscraper.bandzone.selenium.headless:true}") boolean headless,
            @Value("${bzscraper.bandzone.selenium.chromium-binary:}") String chromiumBinary,
            @Value("${bzscraper.bandzone.selenium.chromedriver:}") String chromedriverPath) {
        this.baseUrl = baseUrl;
        this.userLogin = userLogin;
        this.userSecret = userSecret;
        this.bandSlug = bandSlug;
        this.headless = headless;
        this.chromiumBinary = chromiumBinary;
        this.chromedriverPath = chromedriverPath;
    }

    @Override
    public BandzoneSession openSession() throws BandzoneUploadException {
        if (userLogin.isBlank() || userSecret.isBlank() || bandSlug.isBlank()) {
            throw new BandzoneUploadException("Bandzone credentials/band-slug not configured "
                    + "(bzscraper.bandzone.login/password/band-slug).");
        }
        WebDriver driver = newDriver();
        try {
            WebDriverWait wait = new WebDriverWait(driver, WAIT);
            logIn(driver, wait);
            dismissCookies(driver);
            return new SeleniumBandzoneSession(driver, wait, baseUrl, bandSlug);
        } catch (BandzoneUploadException e) {
            driver.quit();
            throw e;
        } catch (RuntimeException e) {
            driver.quit();
            throw new BandzoneUploadException("Bandzone login failed: " + e.getMessage(), e);
        }
    }

    private WebDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1280,1024");
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

        private final WebDriver driver;
        private final WebDriverWait wait;
        private final String baseUrl;
        private final String bandSlug;

        SeleniumBandzoneSession(WebDriver driver, WebDriverWait wait, String baseUrl, String bandSlug) {
            this.driver = driver;
            this.wait = wait;
            this.baseUrl = baseUrl;
            this.bandSlug = bandSlug;
        }

        @Override
        public String createGig(Gig gig) throws BandzoneUploadException {
            try {
                openWizard();
                fillDateAndCity(gig);
                passDuplicateScreen();
                fillInfoAndSend(gig);
                return verifyCreatedAndGetId(gig);
            } catch (BandzoneUploadException e) {
                throw e;
            } catch (RuntimeException e) {
                // Fail only this gig; the session stays open for the rest of the batch.
                throw new BandzoneUploadException("Bandzone create failed for '" + gig.title()
                        + "': " + e.getMessage(), e);
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
            driver.quit();
        }

        private void openWizard() {
            driver.get(baseUrl + "/koncert/zalozit.html?initBandPublicId=" + bandSlug);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.name("start[date]")));
        }

        private void fillDateAndCity(Gig gig) {
            setValue(driver, "[name='start[date]']", gig.schedule().start().format(BZ_DATE));
            setValue(driver, "[name='start[time]']", gig.schedule().start().format(BZ_TIME));

            String city = gig.location().city();
            setValue(driver, "[name='cityId__container[textInput]']", city);
            jsClickByName(driver, "cityId__container[searchButton]");

            WebElement suggestion = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                    "//li[starts-with(normalize-space(.), '" + city + "')]")));
            jsClick(driver, suggestion);

            wait.until(d -> {
                String v = d.findElement(By.name("cityId")).getAttribute("value");
                return v != null && !v.isBlank();
            });
            jsClickByName(driver, "continue");
        }

        private void passDuplicateScreen() {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.name("new")),
                    ExpectedConditions.presenceOfElementLocated(By.name("name"))));
            if (!driver.findElements(By.name("new")).isEmpty()) {
                jsClickByName(driver, "new");
                wait.until(ExpectedConditions.presenceOfElementLocated(By.name("name")));
            }
        }

        private void fillInfoAndSend(Gig gig) {
            setValue(driver, "[name='name']", gig.title());
            selectEntryType(gig.admission().type());
            if (gig.admission().isPaid()) {
                setValue(driver, "[name='entry']", gig.admission().amount());
            }
            if (gig.description() != null && !gig.description().isBlank()) {
                setValue(driver, "[name='info']", gig.description());
            }
            if (gig.facebookUrl() != null && !gig.facebookUrl().isBlank()) {
                setValue(driver, "[name='facebookUrl']", gig.facebookUrl());
            }
            WebElement send = driver.findElement(By.name("send"));
            jsClick(driver, send);
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.stalenessOf(send),
                    ExpectedConditions.urlContains("bandzone.cz")));
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

        private String verifyCreatedAndGetId(Gig gig) throws BandzoneUploadException {
            int year = gig.schedule().start().getYear();
            driver.get(baseUrl + "/" + bandSlug + "?at=gig&gy=" + year);
            String title = gig.title();
            // Find the created gig's link and pull its numeric concert id from the href.
            String id = (String) ((JavascriptExecutor) driver).executeScript(
                    "var t=arguments[0].toLowerCase();"
                            + "var a=Array.from(document.querySelectorAll('a[href*=\"/koncert/\"]'))"
                            + ".find(x=>(x.innerText||'').trim().toLowerCase().includes(t));"
                            + "if(!a) return null;"
                            + "var m=(a.getAttribute('href')||'').match(/\\/koncert\\/(\\d+)/);"
                            + "return m?m[1]:null;",
                    title);
            if (id == null) {
                throw new BandzoneUploadException("Gig '" + title + "' was submitted but did not appear "
                        + "on the band's gig list.");
            }
            logger.info("Bandzone gig created: '{}' (id {})", title, id);
            return id;
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

    private static void jsClickByName(WebDriver driver, String name) {
        ((JavascriptExecutor) driver).executeScript(
                "var e=document.querySelector(arguments[0]); if(e){e.click();}",
                "[name='" + name + "']");
    }

    private static void jsClick(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }
}
