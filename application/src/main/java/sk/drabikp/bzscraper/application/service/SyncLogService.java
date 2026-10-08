package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.application.port.in.UserFacingException;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.domain.model.SyncLogEntry;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Clock;
import java.util.List;

/** The sync log for the user: reading it, and retrying or discarding what the worker gave up on. */
public class SyncLogService implements SyncLogUseCase {

    private final SyncOutbox outbox;
    private final SyncTrigger trigger;
    private final SyncNotifier notifier;
    private final Clock clock;

    public SyncLogService(SyncOutbox outbox, SyncTrigger trigger, SyncNotifier notifier, Clock clock) {
        this.outbox = outbox;
        this.trigger = trigger;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public List<SyncTask> unfinished() {
        return outbox.unfinished();
    }

    @Override
    public List<SyncTask> recent(int limit) {
        return outbox.recent(limit);
    }

    @Override
    public List<SyncLogEntry> log(long taskId) {
        return outbox.log(taskId);
    }

    @Override
    public void retry(long taskId) {
        SyncTask task = outbox.find(taskId).orElseThrow(() -> new IllegalArgumentException("no sync task " + taskId));
        if (task.status() != SyncStatus.FAILED && task.status() != SyncStatus.DISCARDED) {
            throw new UserFacingException("Only a failed or discarded task can be retried — it has moved on meanwhile.");
        }
        outbox.requeue(taskId, clock.instant());
        trigger.wake();
        notifier.changed();
    }

    @Override
    public void discard(long taskId) {
        SyncTask task = outbox.find(taskId).orElseThrow(() -> new IllegalArgumentException("no sync task " + taskId));
        if (task.status() != SyncStatus.FAILED && task.status() != SyncStatus.PENDING) {
            throw new UserFacingException("Only a waiting or failed task can be discarded — it has moved on meanwhile.");
        }
        outbox.discard(taskId, clock.instant());
        notifier.changed();
    }
}
