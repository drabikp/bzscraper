package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepType;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncLogEntry;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The sync outbox: platform work waiting to be done, being done and done, with each
 * task's history. Writes join the caller's transaction, so a catalog change and the tasks
 * it needs commit (or roll back) together. Every state change is logged.
 */
public interface SyncOutbox {

    /** Adds a PENDING task, due now. */
    SyncTask enqueue(GigId gigId, String gigLabel, Platform platform, SyncAction action, Instant now);

    Optional<SyncTask> find(long id);

    /** PENDING and RUNNING tasks of the gig. */
    List<SyncTask> openFor(GigId gigId);

    /** Tasks not settled for good: PENDING, RUNNING and FAILED. */
    List<SyncTask> unfinished();

    /** The newest tasks first. */
    List<SyncTask> recent(int limit);

    List<SyncLogEntry> log(long taskId);

    /** Discards the gig's PENDING tasks on the platform, logging {@code why}; returns them. */
    List<SyncTask> supersede(GigId gigId, Platform platform, String why, Instant now);

    /**
     * Discards the gig's FAILED tasks of these actions on the platform — a newer task does
     * their work now — logging {@code why}; returns them.
     */
    List<SyncTask> replaceFailed(GigId gigId, Platform platform, Set<SyncAction> actions, String why, Instant now);

    /** Follows a gig whose identity changed (an edit moved its date or venue). */
    void move(GigId from, GigId to, String newLabel);

    /** The oldest PENDING task that is due. */
    Optional<SyncTask> nextDue(Instant now);

    /** The next due task on a platform other than {@code skipping} (held back by its breaker). */
    default Optional<SyncTask> nextDue(Instant now, Set<Platform> skipping) {
        return skipping.isEmpty() ? nextDue(now) : due(now).stream().filter(t -> !skipping.contains(t.platform())).findFirst();
    }

    /** Every due task, oldest first. */
    List<SyncTask> due(Instant now);

    /** Every due PENDING task of this action on this platform, oldest first (to run as one batch). */
    List<SyncTask> due(Platform platform, SyncAction action, Instant now);

    List<SyncTask> running();

    /** RUNNING, one more attempt — only if still PENDING (the user may have discarded it); false otherwise. */
    boolean markRunning(long id, Instant now);

    void markDone(long id, String message, Instant now);

    /** Back to PENDING until {@code nextAttempt}. */
    void markRetry(long id, String error, Instant nextAttempt, Instant now);

    void markFailed(long id, String error, Instant now);

    /** A RUNNING task that couldn't start after all: PENDING until {@code nextAttempt}, the attempt not counted. */
    void postpone(long id, String why, Instant nextAttempt, Instant now);

    /** A workflow run moves on to {@code next}: PENDING, due now, logging {@code why}. */
    void advance(long id, StepType next, String why, Instant now);

    /** The user's retry of a FAILED or DISCARDED task: PENDING now, attempts counted afresh. */
    void requeue(long id, Instant now);

    /** The user's discard of a PENDING or FAILED task. */
    void discard(long id, Instant now);
}
