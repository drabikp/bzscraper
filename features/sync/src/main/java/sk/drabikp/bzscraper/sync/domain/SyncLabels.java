package sk.drabikp.bzscraper.sync.domain;

import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** How sync work reads to the user: a task's state, what a change queued. */
public final class SyncLabels {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private SyncLabels() {
    }

    /** Short state of one task: "↻ deleting…", "⏳ update queued", "⏳ update — retry at 10:42", "✗ publish failed". */
    public static String state(SyncTask task) {
        String verb = task.action().verb() + (task.step() != null && task.status() != SyncStatus.DONE
                ? " (" + task.step().label() + ")" : "");
        return switch (task.status()) {
            case RUNNING -> "↻ " + ing(task.action()) + (task.step() != null ? " (" + task.step().label() + ")" : "")
                    + "…";
            case PENDING -> task.retrying()
                    ? "⏳ " + verb + " — retry at " + time(task.nextAttemptAt())
                    : "⏳ " + verb + " queued";
            case FAILED -> "✗ " + verb + " failed";
            case DONE -> "✓ " + verb + " done";
            case DISCARDED -> verb + " discarded";
        };
    }

    public static String status(SyncTask task) {
        return switch (task.status()) {
            case RUNNING -> "running (attempt " + task.attempts() + ")";
            case PENDING -> task.retrying()
                    ? "retry " + (task.attempts() + 1) + "/" + SyncRetryPolicy.maxAttempts() + " at " + time(task.nextAttemptAt())
                    : "queued";
            case FAILED -> "failed — needs you";
            case DONE -> "done";
            case DISCARDED -> "discarded";
        };
    }

    public static String ing(SyncAction action) {
        return switch (action) {
            case PUBLISH -> "publishing";
            case UPDATE -> "updating";
            case CANCEL -> "cancelling";
            case DELETE -> "deleting";
            case REACTIVATE -> "reactivating";
        };
    }

    /** A time of day, e.g. "10:42" (the server's time zone); empty for none. */
    public static String time(Instant at) {
        return at == null ? "" : TIME.format(at.atZone(ZoneId.systemDefault()));
    }

    public static boolean running(List<SyncTask> tasks) {
        return tasks.stream().anyMatch(t -> t.status() == SyncStatus.RUNNING);
    }

    /** What a change put in the outbox, after {@code done} (what the user's action did). */
    public static String queued(String done, QueueResult result, Platforms platforms) {
        StringBuilder text = new StringBuilder(done);
        if (result.queued().isEmpty()) {
            text.append(done.isEmpty() ? "" : " ").append("Nothing to do on the platforms.");
        } else {
            text.append(done.isEmpty() ? "" : " ").append("Queued: ").append(result.queued().stream()
                    .map(t -> t.action().verb() + " on " + platforms.name(t.platform()))
                    .distinct().collect(Collectors.joining(", ")))
                    .append(" — follow it in the Platforms column or the Sync log.");
        }
        if (!result.notQueued().isEmpty()) {
            text.append(" Left out: ").append(String.join("; ", result.notQueued())).append('.');
        }
        return text.toString();
    }
}
