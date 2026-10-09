package sk.drabikp.bzscraper.sync.adapter.in.rest;

import sk.drabikp.bzscraper.sync.adapter.in.rest.api.BreakerJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.LogEntryJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncActionJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncCountsJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncStatusJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncTaskStatusJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.TaskJson;
import sk.drabikp.bzscraper.sync.application.port.in.PlatformBreakerUseCase.Breaker;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.domain.SyncCounts;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** The sync as the API sends it (sync.yaml). */
final class SyncMapping {

    private SyncMapping() {
    }

    /** {@code all} / {@code open} / {@code failed}. */
    static SyncLogUseCase.Show show(String show) {
        return SyncLogUseCase.Show.valueOf(show.toUpperCase(Locale.ROOT));
    }

    static TaskJson toJson(SyncTask t) {
        return new TaskJson(t.id(), t.gigId().token(), t.gigLabel(), t.platform().id(),
                SyncActionJson.fromValue(t.action().name()), SyncTaskStatusJson.fromValue(t.status().name()),
                t.retrying(), t.attempts(), t.createdAt(), t.updatedAt(), t.nextAttemptAt(), t.message(),
                t.step() != null ? t.step().label() : null);
    }

    static List<TaskJson> toJson(List<SyncTask> tasks) {
        return tasks.stream().map(SyncMapping::toJson).toList();
    }

    static List<LogEntryJson> toLogJson(List<SyncLogEntry> log) {
        return log.stream().map(e -> new LogEntryJson(e.at(), e.message())).toList();
    }

    static SyncStatusJson toJson(boolean paused, List<Breaker> breakers, SyncCounts counts, Instant now) {
        return new SyncStatusJson(paused, breakers.stream()
                .map(b -> new BreakerJson(b.platform().id(), b.failures(), b.heldUntil(), b.held(now), b.trial(),
                        b.lastFailure()))
                .toList(),
                new SyncCountsJson(counts.queued(), counts.running(), counts.retrying(), counts.failed()));
    }
}
