package sk.drabikp.bzscraper.domain.model;

import java.time.Instant;

/**
 * One entry of the sync outbox: bring a gig's copy on one platform in line with the
 * catalog. Written in the same transaction as the catalog change that needs it, run
 * later by the dispatcher. {@code gigLabel} keeps the gig readable in the log after the
 * gig itself is gone. {@code nextAttemptAt} is when a PENDING task may run;
 * {@code message} is the latest outcome or error. {@code step} is where a workflow run
 * stands (null: not started, or an action that has no workflow yet).
 */
public record SyncTask(long id, GigId gigId, String gigLabel, Platform platform, SyncAction action,
                       SyncStatus status, int attempts, Instant createdAt, Instant nextAttemptAt,
                       Instant updatedAt, String message, StepType step) {

    /** How a gig is named in the sync log: "2026-09-18 · Eufory + Snaefell, Hranice". */
    public static String labelOf(Gig gig) {
        return gig.schedule().startDate() + " · " + gig.title() + ", " + gig.location().city();
    }

    /** Waiting for a retry (it ran before), rather than for its first run. */
    public boolean retrying() {
        return status == SyncStatus.PENDING && attempts > 0;
    }
}
