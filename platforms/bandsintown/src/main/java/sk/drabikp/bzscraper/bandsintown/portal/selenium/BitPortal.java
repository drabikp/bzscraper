package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

/**
 * The logged-in artist portal one session works in: the browser, the artist, the reply log,
 * and the human-paced ways the pages ({@link BitEventsPage}, {@link BitBulkUpload},
 * {@link BitEventForm}, {@link BitDeleteDialog}) touch it.
 */
final class BitPortal {

    final WebDriver driver;
    final WebDriverWait wait;
    final HumanPacer pacer;
    final PortalReplies replies;
    private final String baseUrl;
    private final String artistId;

    public BitPortal(WebDriver driver, WebDriverWait wait, HumanPacer pacer, String baseUrl, String artistId) {
        this.driver = driver;
        this.wait = wait;
        this.pacer = pacer;
        this.replies = new PortalReplies(driver);
        this.baseUrl = baseUrl;
        this.artistId = artistId;
    }

    /** A page of the artist, e.g. {@code events/upcoming}. */
    void open(String artistPage) {
        driver.get(baseUrl + "/artists/" + artistId + "/" + artistPage);
    }

    public void pause() {
        pacer.pause();
    }

    public void longPause() {
        pacer.longPause();
    }

    void click(WebElement element) {
        pacer.click(driver, element);
    }

    void type(WebElement field, String text) {
        pacer.type(driver, field, text);
    }

    void typeIntoFocused(String text) {
        pacer.typeIntoFocused(driver, text);
    }

    /** The visible button with this text — the last one, as dialogs render after the page. */
    WebElement visibleButton(String text) {
        return wait.until(d -> {
            List<WebElement> visible = d.findElements(By.xpath("//button[normalize-space()='" + text + "']"))
                    .stream().filter(WebElement::isDisplayed).toList();
            return visible.isEmpty() ? null : visible.getLast();
        });
    }

    WebElement firstVisible(By by) {
        return driver.findElements(by).stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
    }

    /** Closes promo pop-ups ("Boost your shows") that may cover the page. */
    void closePopups() {
        for (WebElement close : driver.findElements(By.cssSelector("button[aria-label='Close dialog']"))) {
            if (close.isDisplayed()) {
                click(close);
                pause();
            }
        }
    }

    Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }
}
