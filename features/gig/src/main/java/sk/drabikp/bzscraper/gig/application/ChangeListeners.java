package sk.drabikp.bzscraper.gig.application;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Whoever follows something that changes (an open page following the sync or a check): each is
 * told on the thread that made the change, and one that fails doesn't stop the others.
 */
public final class ChangeListeners {

    private static final System.Logger log = System.getLogger(ChangeListeners.class.getName());

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Follows the changes until the returned subscription is cancelled. */
    public Subscription add(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    public void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.log(System.Logger.Level.DEBUG, "A listener could not be told about a change", e);
            }
        }
    }

    /** Ends following. */
    @FunctionalInterface
    public interface Subscription {

        void cancel();
    }
}
