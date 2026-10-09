package sk.drabikp.bzscraper.sync.adapter.in.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.PlatformBreakerUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The sync over HTTP: every platform task and its history, Retry and Discard of failed work,
 * Pause / Resume, and the platforms held back by their circuit breakers.
 */
@RestController
@RequestMapping("/api/sync")
class SyncEndpoint {

    private static final int LIMIT = 500;

    private final SyncLogUseCase syncLog;
    private final PauseSyncUseCase pause;
    private final PlatformBreakerUseCase breakers;
    private final Platforms platforms;
    private final Clock clock;
    private final LiveUpdates live;

    SyncEndpoint(SyncLogUseCase syncLog, PauseSyncUseCase pause, PlatformBreakerUseCase breakers, Platforms platforms,
                 Clock clock, LiveUpdates live) {
        this.syncLog = syncLog;
        this.pause = pause;
        this.breakers = breakers;
        this.platforms = platforms;
        this.clock = clock;
        this.live = live;
    }

    /** A platform task; {@code step}: where its workflow is (e.g. "bulk edit"); {@code gigId}: the gig's token. */
    record TaskJson(long id, String gigId, String gigLabel, String platform, String action, String status,
                    boolean retrying, int attempts, String createdAt, String updatedAt, String nextAttemptAt,
                    String message, String step) {

        static TaskJson of(SyncTask t) {
            return new TaskJson(t.id(), t.gigId().token(), t.gigLabel(), t.platform().id(), t.action().name(),
                    t.status().name(), t.retrying(), t.attempts(), iso(t.createdAt()), iso(t.updatedAt()),
                    iso(t.nextAttemptAt()), t.message(), t.step() != null ? t.step().label() : null);
        }
    }

    record LogJson(String at, String message) {
    }

    record BreakerJson(String platform, int failures, String heldUntil, boolean held, boolean trial,
                       String lastFailure) {
    }

    /** {@code counts}: open tasks by what they are doing now. */
    record StatusJson(boolean paused, List<BreakerJson> breakers, Counts counts) {
    }

    record Counts(long queued, long running, long retrying, long failed) {
    }

    /** {@code show}: {@code all} (the latest), {@code open} (not finished), {@code failed} (waiting for the user). */
    @GetMapping("/tasks")
    List<TaskJson> tasks(@RequestParam(defaultValue = "all") String show) {
        List<SyncTask> tasks = switch (show) {
            case "open" -> syncLog.unfinished().stream().filter(t -> t.status().open()).toList().reversed();
            case "failed" -> syncLog.unfinished().stream().filter(t -> t.status() == SyncStatus.FAILED)
                    .toList().reversed();
            default -> syncLog.recent(LIMIT);
        };
        return tasks.stream().map(TaskJson::of).toList();
    }

    @GetMapping("/tasks/{id}/log")
    List<LogJson> log(@PathVariable long id) {
        return syncLog.log(id).stream().map(e -> new LogJson(iso(e.at()), e.message())).toList();
    }

    @PostMapping("/tasks/{id}/retry")
    void retry(@PathVariable long id) {
        syncLog.retry(id);
        changed();
    }

    @PostMapping("/tasks/{id}/discard")
    void discard(@PathVariable long id) {
        syncLog.discard(id);
        changed();
    }

    @GetMapping("/status")
    StatusJson status() {
        Instant now = clock.instant();
        List<SyncTask> open = syncLog.unfinished().stream().filter(t -> t.status().open()).toList();
        return new StatusJson(pause.paused(), breakers.breakers().stream()
                .map(b -> new BreakerJson(b.platform().id(), b.failures(), iso(b.heldUntil()), b.held(now), b.trial(),
                        b.lastFailure()))
                .toList(),
                new Counts(open.stream().filter(t -> t.status() == SyncStatus.PENDING && !t.retrying()).count(),
                        open.stream().filter(t -> t.status() == SyncStatus.RUNNING).count(),
                        open.stream().filter(t -> t.status() == SyncStatus.PENDING && t.retrying()).count(),
                        open.stream().filter(t -> t.status() == SyncStatus.FAILED).count()));
    }

    @PostMapping("/pause")
    void pause() {
        pause.pause();
        changed();
    }

    @PostMapping("/resume")
    void resume() {
        pause.resume();
        changed();
    }

    @PostMapping("/breakers/{platform}/resume")
    void resumePlatform(@PathVariable String platform) {
        breakers.resume(platforms.find(platform).orElseThrow(() ->
                new IllegalArgumentException("not a platform: " + platform)));
        changed();
    }

    private void changed() {
        live.changed(LiveUpdates.Topic.SYNC);
        live.changed(LiveUpdates.Topic.GIGS);
    }

    private static String iso(Instant at) {
        return at == null ? null : at.toString();
    }
}
