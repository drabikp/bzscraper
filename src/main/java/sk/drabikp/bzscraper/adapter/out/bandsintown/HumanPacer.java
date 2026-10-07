package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes the portal automation behave like a person, so the account is not flagged:
 * random pauses between steps, real mouse moves and clicks (never script clicks),
 * and typing one key at a time at human speed with the odd hesitation.
 */
final class HumanPacer {

    private final long minPauseMs;
    private final long maxPauseMs;

    HumanPacer(long minPauseMs, long maxPauseMs) {
        this.minPauseMs = Math.max(0, minPauseMs);
        this.maxPauseMs = Math.max(this.minPauseMs, maxPauseMs);
    }

    /** A "reading the page" pause between steps. */
    void pause() {
        sleep(between(minPauseMs, maxPauseMs));
    }

    /** A longer break, e.g. between two upload batches. */
    void longPause() {
        sleep(between(minPauseMs * 3, maxPauseMs * 4));
    }

    /** Scrolls the element into view, moves the mouse onto it and clicks. */
    void click(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center',inline:'nearest'});", element);
        sleep(between(150, 450));
        int jitterX = jitter(element.getSize().getWidth());
        int jitterY = jitter(element.getSize().getHeight());
        new Actions(driver)
                .moveToElement(element, jitterX, jitterY)
                .pause(Duration.ofMillis(between(80, 260)))
                .click()
                .perform();
    }

    /** Clicks into the field and types the text key by key. */
    void type(WebDriver driver, WebElement field, String text) {
        click(driver, field);
        typeIntoFocused(driver, text);
    }

    /** Types into whatever has focus (e.g. auto-advancing code boxes). */
    void typeIntoFocused(WebDriver driver, String text) {
        for (char c : text.toCharArray()) {
            new Actions(driver).sendKeys(String.valueOf(c)).perform();
            sleep(ThreadLocalRandom.current().nextInt(100) < 6 ? between(300, 700) : between(60, 190));
        }
    }

    private static int jitter(int size) {
        int max = Math.min(3, size / 4);
        return max <= 0 ? 0 : ThreadLocalRandom.current().nextInt(-max, max + 1);
    }

    private static long between(long min, long max) {
        return max <= min ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pacing", e);
        }
    }
}
