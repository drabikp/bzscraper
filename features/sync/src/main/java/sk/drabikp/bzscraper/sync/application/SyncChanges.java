package sk.drabikp.bzscraper.sync.application;

import sk.drabikp.bzscraper.gig.application.ChangeListeners.Subscription;
import sk.drabikp.bzscraper.gig.application.ChangeListeners;
import sk.drabikp.bzscraper.sync.application.port.in.WatchSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.out.SyncNotifier;

/** Passes "the sync changed" from the engine and the use cases to every page that follows it. */
public class SyncChanges implements SyncNotifier, WatchSyncUseCase {

    private final ChangeListeners listeners = new ChangeListeners();

    @Override
    public Subscription watch(Runnable onChange) {
        return listeners.add(onChange);
    }

    @Override
    public void changed() {
        listeners.changed();
    }
}
