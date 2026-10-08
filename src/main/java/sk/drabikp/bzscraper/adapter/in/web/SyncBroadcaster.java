package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Passes "the sync outbox changed" to every open page that shows sync state; each page
 * refreshes itself through {@code UI.access} (server push). Called on the sync worker's
 * thread and after user actions.
 */
@Component
public class SyncBroadcaster implements SyncNotifier {

    private static final Logger log = LoggerFactory.getLogger(SyncBroadcaster.class);

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public Registration register(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.debug("A page could not be told about a sync change", e);
            }
        }
    }
}
