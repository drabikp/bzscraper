package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarmBrowserTest {

    private final Semaphore oneBrowser = new Semaphore(1);

    @Test
    void the_next_session_takes_over_the_parked_browser() {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMinutes(1), "test");
        WebDriver driver = mock(WebDriver.class);

        warm.park(driver);

        assertThat(warm.take()).isSameAs(driver);
        assertThat(warm.take()).as("taken once").isNull();
        verify(driver, never()).quit();
    }

    @Test
    void a_browser_that_died_meanwhile_is_closed_not_handed_out() {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMinutes(1), "test");
        WebDriver driver = mock(WebDriver.class);
        when(driver.getWindowHandle()).thenThrow(new WebDriverException("gone"));

        warm.park(driver);

        assertThat(warm.take()).isNull();
        verify(driver).quit();
    }

    @Test
    void an_idle_browser_is_closed_unless_someone_holds_the_browser() throws Exception {
        WarmBrowser warm = new WarmBrowser(oneBrowser, Duration.ofMillis(50), "test");
        WebDriver idle = mock(WebDriver.class);
        warm.park(idle);
        verify(idle, timeout(2000)).quit();

        WebDriver busy = mock(WebDriver.class);
        oneBrowser.acquire();                       // a session is using the platform's browser
        warm.park(busy);
        Thread.sleep(200);
        verify(busy, never()).quit();
        oneBrowser.release();
        warm.close();
        verify(busy).quit();
    }
}
