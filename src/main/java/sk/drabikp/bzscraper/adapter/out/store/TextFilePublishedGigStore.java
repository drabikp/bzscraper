package sk.drabikp.bzscraper.adapter.out.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Flat-file {@link PublishedGigStore}: one line per (platform, gig) as
 * {@code PLATFORM\tISO-DATE\tvenue\texternalRef} (ref may be empty). Kept in an
 * in-memory map loaded once; every change rewrites the file (small, single-user).
 * Synchronized because the Vaadin UI may publish from concurrent threads.
 */
@Component
public class TextFilePublishedGigStore implements PublishedGigStore {

    private static final String SEP = "\t";

    private final Path file;
    private Map<String, String> refsByKey; // key -> externalRef ("" if none)

    public TextFilePublishedGigStore(
            @Value("${bzscraper.publish.state-file:}") String configuredPath) {
        this.file = (configuredPath == null || configuredPath.isBlank())
                ? Paths.get(System.getProperty("user.home"), ".bzscraper", "published-gigs.tsv")
                : Paths.get(configuredPath);
    }

    @Override
    public synchronized boolean isPublished(Platform platform, GigId gigId) {
        ensureLoaded();
        return refsByKey.containsKey(key(platform, gigId));
    }

    @Override
    public synchronized Optional<String> externalRef(Platform platform, GigId gigId) {
        ensureLoaded();
        String ref = refsByKey.get(key(platform, gigId));
        return (ref == null || ref.isBlank()) ? Optional.empty() : Optional.of(ref);
    }

    @Override
    public synchronized void record(Platform platform, GigId gigId, String externalRef) {
        ensureLoaded();
        refsByKey.put(key(platform, gigId), externalRef == null ? "" : externalRef);
        persist();
    }

    @Override
    public synchronized void remove(Platform platform, GigId gigId) {
        ensureLoaded();
        if (refsByKey.remove(key(platform, gigId)) != null) {
            persist();
        }
    }

    private void ensureLoaded() {
        if (refsByKey != null) {
            return;
        }
        refsByKey = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(SEP, -1); // keep trailing empty ref
                if (parts.length >= 3) {
                    String key = parts[0] + SEP + parts[1] + SEP + parts[2];
                    String ref = parts.length >= 4 ? parts[3] : "";
                    refsByKey.put(key, ref);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read published-gig state from " + file, e);
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>(refsByKey.size());
        refsByKey.forEach((key, ref) -> lines.add(key + SEP + ref));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist published-gig state to " + file, e);
        }
    }

    private static String key(Platform platform, GigId gigId) {
        String date = gigId.date() != null ? gigId.date().toString() : "";
        return platform.name() + SEP + date + SEP + gigId.venue();
    }
}
