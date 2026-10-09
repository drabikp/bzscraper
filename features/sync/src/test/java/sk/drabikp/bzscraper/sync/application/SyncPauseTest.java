package sk.drabikp.bzscraper.sync.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncPauseTest {

    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.Settings settings = new SyncFakes.Settings();

    @Test
    void a_pause_survives_a_restart_until_the_user_resumes() {
        new SyncPause(signals, signals, settings).pause();

        SyncPause afterRestart = new SyncPause(signals, signals, settings);
        assertThat(afterRestart.paused()).isTrue();

        afterRestart.resume();
        assertThat(new SyncPause(signals, signals, settings).paused()).isFalse();
        assertThat(signals.wakes).as("resuming wakes the worker").isEqualTo(1);
    }
}
