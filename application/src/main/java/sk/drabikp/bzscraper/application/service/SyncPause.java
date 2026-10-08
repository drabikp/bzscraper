package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.application.port.out.SettingsStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;

/**
 * The sync's pause switch: read by the dispatcher and the engine before each piece of work.
 * It survives a restart — paused stays paused until the user resumes.
 */
public class SyncPause implements PauseSyncUseCase {

    static final String SETTING = "sync.paused";

    private final SyncTrigger trigger;
    private final SyncNotifier notifier;
    private final SettingsStore settings;
    private volatile boolean paused;

    public SyncPause(SyncTrigger trigger, SyncNotifier notifier, SettingsStore settings) {
        this.trigger = trigger;
        this.notifier = notifier;
        this.settings = settings;
        this.paused = settings.get(SETTING).map(Boolean::parseBoolean).orElse(false);
    }

    @Override
    public void pause() {
        settings.put(SETTING, "true");
        paused = true;
        notifier.changed();
    }

    @Override
    public void resume() {
        settings.put(SETTING, null);
        paused = false;
        notifier.changed();
        trigger.wake();
    }

    @Override
    public boolean paused() {
        return paused;
    }
}
