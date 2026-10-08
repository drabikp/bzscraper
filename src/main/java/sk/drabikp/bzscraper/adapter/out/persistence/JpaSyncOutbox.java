package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncLogEntry;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA-backed {@link SyncOutbox} (tables {@code sync_task} + {@code sync_log}) in the
 * catalog's database: enqueueing joins the caller's transaction, which is what makes it
 * an outbox. Every state change writes a log line.
 */
@Component
@Transactional
public class JpaSyncOutbox implements SyncOutbox {

    private static final List<String> OPEN = List.of(SyncStatus.PENDING.name(), SyncStatus.RUNNING.name());
    private static final List<String> UNFINISHED =
            List.of(SyncStatus.PENDING.name(), SyncStatus.RUNNING.name(), SyncStatus.FAILED.name());

    private final SyncTaskJpaRepository tasks;
    private final SyncLogJpaRepository log;

    public JpaSyncOutbox(SyncTaskJpaRepository tasks, SyncLogJpaRepository log) {
        this.tasks = tasks;
        this.log = log;
    }

    @Override
    public SyncTask enqueue(GigId gigId, String gigLabel, Platform platform, SyncAction action, Instant now) {
        SyncTaskEntity task = tasks.save(new SyncTaskEntity(GigEntityMapper.serializeId(gigId), gigLabel, platform,
                action, now));
        write(task, now, "queued");
        return task.toTask();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SyncTask> find(long id) {
        return tasks.findById(id).map(SyncTaskEntity::toTask);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTask> openFor(GigId gigId) {
        return toTasks(tasks.findByGigIdAndStatusInOrderByIdAsc(GigEntityMapper.serializeId(gigId), OPEN));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTask> unfinished() {
        return toTasks(tasks.findByStatusInOrderByIdAsc(UNFINISHED));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTask> recent(int limit) {
        return toTasks(tasks.findAllByOrderByIdDesc(Limit.of(limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncLogEntry> log(long taskId) {
        return log.findByTaskIdOrderByIdAsc(taskId).stream().map(SyncLogEntity::toEntry).toList();
    }

    @Override
    public List<SyncTask> supersede(GigId gigId, Platform platform, String why, Instant now) {
        List<SyncTaskEntity> pending = tasks.findByGigIdAndPlatformAndStatusOrderByIdAsc(
                GigEntityMapper.serializeId(gigId), platform.name(), SyncStatus.PENDING.name());
        for (SyncTaskEntity task : pending) {
            task.change(SyncStatus.DISCARDED, null, "superseded: " + why, now);
            write(task, now, "superseded: " + why);
        }
        return toTasks(pending);
    }

    @Override
    public List<SyncTask> replaceFailed(GigId gigId, Platform platform, Set<SyncAction> actions, String why,
                                        Instant now) {
        List<SyncTaskEntity> failed = tasks.findByGigIdAndPlatformAndStatusOrderByIdAsc(
                        GigEntityMapper.serializeId(gigId), platform.name(), SyncStatus.FAILED.name()).stream()
                .filter(task -> actions.contains(task.toTask().action()))
                .toList();
        for (SyncTaskEntity task : failed) {
            task.change(SyncStatus.DISCARDED, null, "replaced: " + why, now);
            write(task, now, "replaced: " + why);
        }
        return toTasks(failed);
    }

    @Override
    public void move(GigId from, GigId to, String newLabel) {
        for (SyncTaskEntity task : tasks.findByGigId(GigEntityMapper.serializeId(from))) {
            task.moveTo(GigEntityMapper.serializeId(to), newLabel);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SyncTask> nextDue(Instant now) {
        return tasks.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(SyncStatus.PENDING.name(), now)
                .map(SyncTaskEntity::toTask);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTask> due(Platform platform, SyncAction action, Instant now) {
        return toTasks(tasks.findByPlatformAndActionAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                platform.name(), action.name(), SyncStatus.PENDING.name(), now));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTask> running() {
        return toTasks(tasks.findByStatusInOrderByIdAsc(List.of(SyncStatus.RUNNING.name())));
    }

    @Override
    public boolean markRunning(long id, Instant now) {
        SyncTaskEntity task = get(id);
        if (task.status() != SyncStatus.PENDING) {
            return false;
        }
        task.startAttempt(now);
        write(task, now, "attempt " + task.getAttempts() + " started");
        return true;
    }

    @Override
    public void markDone(long id, String message, Instant now) {
        SyncTaskEntity task = get(id);
        task.change(SyncStatus.DONE, null, message, now);
        write(task, now, message == null || message.isBlank() ? "done" : "done — " + message);
    }

    @Override
    public void markRetry(long id, String error, Instant nextAttempt, Instant now) {
        SyncTaskEntity task = get(id);
        task.change(SyncStatus.PENDING, nextAttempt, error, now);
        write(task, now, "failed: " + error + " — will retry at " + nextAttempt);
    }

    @Override
    public void markFailed(long id, String error, Instant now) {
        SyncTaskEntity task = get(id);
        task.change(SyncStatus.FAILED, null, error, now);
        write(task, now, "failed: " + error + " — waiting for you to Retry or Discard");
    }

    @Override
    public void requeue(long id, Instant now) {
        SyncTaskEntity task = get(id);
        task.change(SyncStatus.PENDING, now, null, now);
        task.resetAttempts();
        write(task, now, "retried by you");
    }

    @Override
    public void discard(long id, Instant now) {
        SyncTaskEntity task = get(id);
        task.change(SyncStatus.DISCARDED, null, "discarded by you", now);
        write(task, now, "discarded by you");
    }

    private SyncTaskEntity get(long id) {
        return tasks.findById(id).orElseThrow(() -> new IllegalArgumentException("no sync task " + id));
    }

    private void write(SyncTaskEntity task, Instant at, String message) {
        log.save(new SyncLogEntity(task.getId(), at, message));
    }

    private static List<SyncTask> toTasks(List<SyncTaskEntity> entities) {
        return entities.stream().map(SyncTaskEntity::toTask).toList();
    }
}
