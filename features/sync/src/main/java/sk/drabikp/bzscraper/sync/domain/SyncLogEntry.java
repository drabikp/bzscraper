package sk.drabikp.bzscraper.sync.domain;

import java.time.Instant;

/** One line of a sync task's history: queued, attempt started, done, failed, retried, discarded. */
public record SyncLogEntry(long taskId, Instant at, String message) {
}
