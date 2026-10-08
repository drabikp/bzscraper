package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.notification.Notification;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;
import sk.drabikp.bzscraper.domain.service.SyncRetryPolicy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** How sync tasks read on the pages. */
final class SyncLabels {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm:ss");

    private SyncLabels() {
    }

    /** Short state of one task: "↻ deleting…", "⏳ update queued", "⏳ update — retry at 10:42", "✗ publish failed". */
    static String state(SyncTask task) {
        String verb = task.action().verb();
        return switch (task.status()) {
            case RUNNING -> "↻ " + ing(task.action()) + "…";
            case PENDING -> task.retrying()
                    ? "⏳ " + verb + " — retry at " + time(task.nextAttemptAt())
                    : "⏳ " + verb + " queued";
            case FAILED -> "✗ " + verb + " failed";
            case DONE -> "✓ " + verb + " done";
            case DISCARDED -> verb + " discarded";
        };
    }

    static String status(SyncTask task) {
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

    static String ing(SyncAction action) {
        return switch (action) {
            case PUBLISH -> "publishing";
            case UPDATE -> "updating";
            case CANCEL -> "cancelling";
            case DELETE -> "deleting";
            case REACTIVATE -> "reactivating";
        };
    }

    static String when(Instant at) {
        return at == null ? "" : WHEN.format(at.atZone(ZoneId.systemDefault()));
    }

    static String time(Instant at) {
        return at == null ? "" : TIME.format(at.atZone(ZoneId.systemDefault()));
    }

    static boolean running(List<SyncTask> tasks) {
        return tasks.stream().anyMatch(t -> t.status() == SyncStatus.RUNNING);
    }

    /** Tells the user what a change put in the outbox. */
    static void showQueued(String done, QueueResult result) {
        StringBuilder text = new StringBuilder(done);
        if (result.queued().isEmpty()) {
            text.append(done.isEmpty() ? "" : " ").append("Nothing to do on the platforms.");
        } else {
            text.append(done.isEmpty() ? "" : " ").append("Queued: ").append(result.queued().stream()
                    .map(t -> t.action().verb() + " on " + PublishSummaries.label(t.platform()))
                    .distinct().collect(Collectors.joining(", ")))
                    .append(" — follow it in the Platforms column or the Sync log.");
        }
        if (!result.notQueued().isEmpty()) {
            text.append(" Left out: ").append(String.join("; ", result.notQueued())).append('.');
        }
        Notification.show(text.toString(), 6000, Notification.Position.BOTTOM_START);
    }
}
