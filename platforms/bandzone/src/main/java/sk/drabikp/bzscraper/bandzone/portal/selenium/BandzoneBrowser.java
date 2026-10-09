package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * The logged-in Bandzone band admin one session works in, and the ways its pages
 * ({@link BandzoneCreateWizard}, {@link BandzoneUpdateForm}, {@link BandzoneDeleteTab},
 * {@link BandzoneLineupPage}) touch it. Bandzone's forms are plain server-rendered forms with
 * jQuery widgets: values are set by script (with input/change events), buttons clicked by
 * script — no human pacing is needed there.
 */
final class BandzoneBrowser {

    private static final Duration RELOAD_WAIT = Duration.ofSeconds(5);

    final WebDriver driver;
    public final WebDriverWait wait;
    final String baseUrl;
    public final String bandSlug;

    public BandzoneBrowser(WebDriver driver, WebDriverWait wait, String baseUrl, String bandSlug) {
        this.driver = driver;
        this.wait = wait;
        this.baseUrl = baseUrl;
        this.bandSlug = bandSlug;
    }

    /** A Bandzone page, e.g. {@code /koncert/563380/update}. */
    public void open(String path) {
        driver.get(baseUrl + path);
    }

    /** Sets a field's value and fires input/change, as typing would. */
    void setValue(String cssSelector, String value) {
        js("var e=document.querySelector(arguments[0]);"
                        + "if(e){e.value=arguments[1];"
                        + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "e.dispatchEvent(new Event('change',{bubbles:true}));}",
                cssSelector, value);
    }

    void clickByName(String name) {
        js("var e=document.querySelector(arguments[0]); if(e){e.click();}", "[name='" + name + "']");
    }

    void click(WebElement element) {
        js("arguments[0].click();", element);
    }

    /**
     * Picking from a city/venue autocomplete (or an uploaded poster) re-renders the update form
     * via AJAX about a second later, re-posting its current values; anything typed before that
     * lands is lost. Waits for the old form to go (the create wizard does not reload, so a
     * timeout just means there was nothing to wait for).
     */
    void awaitReload(WebElement oldFormElement) {
        try {
            new WebDriverWait(driver, RELOAD_WAIT).until(ExpectedConditions.stalenessOf(oldFormElement));
        } catch (TimeoutException e) {
            // no reload
        }
    }

    Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }
}
