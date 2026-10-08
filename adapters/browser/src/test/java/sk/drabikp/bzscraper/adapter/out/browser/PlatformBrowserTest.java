package sk.drabikp.bzscraper.adapter.out.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformBrowserTest {

    @TempDir
    Path tmp;

    private final AtomicInteger started = new AtomicInteger();
    private PlatformBrowser browser;

    private PlatformBrowser browser() {
        TestChromium.assumeInstalled();
        browser = new PlatformBrowser(TestChromium.settings("test", tmp.resolve("profile")),
                new PlatformBrowser.Customizer() {
                    @Override
                    public void started(org.openqa.selenium.chrome.ChromeDriver driver) {
                        started.incrementAndGet();
                    }
                });
        return browser;
    }

    @AfterEach
    void close() {
        if (browser != null) {
            browser.close();
        }
    }

    @Test
    void one_operation_at_a_time_the_next_finds_it_busy_or_waits() {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease first = browser.acquire(Duration.ZERO).orElseThrow();

        assertThat(browser.acquire(Duration.ZERO)).as("busy, not waited for").isEmpty();
        assertThat(browser.acquire(Duration.ofMillis(200))).as("busy for the whole wait").isEmpty();
        first.keepWarm();
        assertThat(browser.acquire(Duration.ZERO)).isPresent();
    }

    @Test
    void a_session_kept_warm_is_taken_over_and_a_discarded_one_starts_afresh() throws Exception {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease first = browser.acquire(Duration.ZERO).orElseThrow();
        assertThat(first.wasWarm()).isFalse();
        first.keepWarm();
        first.keepWarm();                               // ending twice is harmless

        PlatformBrowser.Lease second = browser.acquire(Duration.ZERO).orElseThrow();
        assertThat(second.wasWarm()).isTrue();
        assertThat(second.driver()).isSameAs(first.driver());
        second.discard();

        Optional<PlatformBrowser.Lease> third = browser.acquire(Duration.ZERO);
        assertThat(third).get().extracting(PlatformBrowser.Lease::wasWarm).isEqualTo(false);
        assertThat(started).hasValue(2);
        assertThat(Files.getPosixFilePermissions(tmp.resolve("profile"))).hasSize(3);   // owner only
        third.get().keepWarm();
    }

    @Test
    void a_failure_in_the_browser_leaves_a_screenshot_and_becomes_the_platforms_failure() {
        PlatformBrowser browser = browser();
        PlatformBrowser.Lease lease = browser.acquire(Duration.ZERO).orElseThrow();
        Path shot = Path.of(System.getProperty("java.io.tmpdir"), "bzscraper-test-probe.png");

        assertThatThrownBy(() -> lease.guarded("probe", () -> {
            throw new IllegalStateException("no such element");
        }, e -> new Exception("Platform failed: " + e.getMessage()))).hasMessage("Platform failed: no such element");
        assertThat(shot).exists();
        lease.discard();
    }
}
