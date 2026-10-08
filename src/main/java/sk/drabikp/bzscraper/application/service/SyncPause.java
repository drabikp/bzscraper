package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;

/** The sync's pause switch: read by the dispatcher and the engine before each piece of work. */
public class SyncPause implements PauseSyncUseCase {

    private final SyncTrigger trigger;
    private final SyncNotifier notifier;
    private volatile boolean paused;

    public SyncPause(SyncTrigger trigger, SyncNotifier notifier) {
        this.trigger = trigger;
        this.notifier = notifier;
    }

    @Override
    public void pause() {
        paused = true;
        notifier.changed();
    }

    @Override
    public void resume() {
        paused = false;
        notifier.changed();
        trigger.wake();
    }

    @Override
    public boolean paused() {
        return paused;
    }
}
