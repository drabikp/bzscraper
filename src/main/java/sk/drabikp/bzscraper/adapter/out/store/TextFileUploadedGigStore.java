package sk.drabikp.bzscraper.adapter.out.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.UploadedGigStore;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Flat-file {@link UploadedGigStore}: one line per uploaded gig,
 * {@code PLATFORM\tISO-DATE\tnormalized-venue}, in a tab-separated file.
 *
 * Dependency-free and human-readable. Loaded once into an in-memory set; new
 * marks are appended to the file so state survives restart. Methods are
 * synchronized because a Vaadin UI may trigger publishes from concurrent threads.
 */
@Component
public class TextFileUploadedGigStore implements UploadedGigStore {

    private static final String SEP = "\t";

    private final Path file;
    private Set<String> cache;

    public TextFileUploadedGigStore(
            @Value("${bzscraper.publish.state-file:}") String configuredPath) {
        this.file = (configuredPath == null || configuredPath.isBlank())
                ? Paths.get(System.getProperty("user.home"), ".bzscraper", "uploaded-gigs.tsv")
                : Paths.get(configuredPath);
    }

    @Override
    public synchronized boolean isUploaded(Platform platform, GigId key) {
        ensureLoaded();
        return cache.contains(line(platform, key));
    }

    @Override
    public synchronized void markUploaded(Platform platform, Collection<GigId> keys) {
        ensureLoaded();
        List<String> newLines = new ArrayList<>();
        for (GigId key : keys) {
            String line = line(platform, key);
            if (cache.add(line)) {
                newLines.add(line);
            }
        }
        if (newLines.isEmpty()) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, newLines, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist uploaded-gig state to " + file, e);
        }
    }

    private void ensureLoaded() {
        if (cache != null) {
            return;
        }
        cache = new HashSet<>();
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    cache.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded-gig state from " + file, e);
        }
    }

    private static String line(Platform platform, GigId key) {
        String date = key.date() != null ? key.date().toString() : "";
        return platform.name() + SEP + date + SEP + key.venue();
    }
}
