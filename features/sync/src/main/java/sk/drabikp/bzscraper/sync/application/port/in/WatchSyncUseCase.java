package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.gig.application.ChangeListeners.Subscription;

/** Lets a page follow the sync: told whenever the outbox, a breaker or the pause changed. */
public interface WatchSyncUseCase {

    /** {@code onChange} runs on the thread that changed the sync (the worker's, or a user's). */
    Subscription watch(Runnable onChange);
}
