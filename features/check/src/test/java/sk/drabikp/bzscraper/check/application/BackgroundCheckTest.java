package sk.drabikp.bzscraper.check.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundCheckTest {

    private final Check check = new Check();
    private final List<Runnable> background = new ArrayList<>();
    private final BackgroundCheck start = new BackgroundCheck(check, background::add);

    @Test
    void a_check_the_user_starts_runs_in_the_background() {
        start.start();

        assertThat(check.checks).isZero();
        background.getFirst().run();
        assertThat(check.checks).isEqualTo(1);
    }

    @Test
    void nothing_starts_while_a_check_runs_and_a_failed_one_doesnt_escape() {
        check.running = true;
        start.start();
        assertThat(background).isEmpty();

        check.running = false;
        check.fails = true;
        start.start();
        background.getFirst().run();                          // logged, not thrown into the executor
    }

    private static final class Check implements CheckPlatformsUseCase {

        private boolean running;
        private boolean fails;
        private int checks;

        @Override
        public Optional<PlatformCheck> check() {
            if (fails) {
                throw new IllegalStateException("a platform's page changed");
            }
            checks++;
            return Optional.empty();
        }

        @Override
        public Optional<PlatformCheck> lastCheck() {
            return Optional.empty();
        }

        @Override
        public boolean running() {
            return running;
        }

        @Override
        public void forget(Platform platform, GigId gigId) {
        }

        @Override
        public void dismiss(Platform platform, GigId gigId) {
        }
    }
}
