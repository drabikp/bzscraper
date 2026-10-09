package sk.drabikp.bzscraper.sync.application.port.out;

import java.util.Optional;

/** Small settings the app keeps across restarts (e.g. the sync paused by the user). */
public interface SettingsStore {

    Optional<String> get(String name);

    /** Sets {@code name}; {@code null} removes it. */
    void put(String name, String value);
}
