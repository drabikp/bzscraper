package sk.drabikp.bzscraper.sync.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.LogEntryJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncApi;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.SyncStatusJson;
import sk.drabikp.bzscraper.sync.adapter.in.rest.api.TaskJson;
import sk.drabikp.bzscraper.sync.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.PlatformBreakerUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;

import java.time.Clock;
import java.util.List;

/** Serves the sync (sync.yaml). The use cases announce their changes on the live updates themselves. */
@RestController
class SyncController implements SyncApi {

    private final SyncLogUseCase syncLog;
    private final PauseSyncUseCase pause;
    private final PlatformBreakerUseCase breakers;
    private final Platforms platforms;
    private final Clock clock;

    SyncController(SyncLogUseCase syncLog, PauseSyncUseCase pause, PlatformBreakerUseCase breakers,
                   Platforms platforms, Clock clock) {
        this.syncLog = syncLog;
        this.pause = pause;
        this.breakers = breakers;
        this.platforms = platforms;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<List<TaskJson>> listTasks(String show) {
        return ResponseEntity.ok(SyncMapping.toJson(syncLog.tasks(SyncMapping.show(show))));
    }

    @Override
    public ResponseEntity<List<LogEntryJson>> taskLog(Long id) {
        return ResponseEntity.ok(SyncMapping.toLogJson(syncLog.log(id)));
    }

    @Override
    public ResponseEntity<Void> retryTask(Long id) {
        syncLog.retry(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> discardTask(Long id) {
        syncLog.discard(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SyncStatusJson> syncStatus() {
        return ResponseEntity.ok(SyncMapping.toJson(pause.paused(), breakers.breakers(), syncLog.counts(),
                clock.instant()));
    }

    @Override
    public ResponseEntity<Void> pauseSync() {
        pause.pause();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resumeSync() {
        pause.resume();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resumePlatform(String platform) {
        breakers.resume(platforms.get(platform));
        return ResponseEntity.noContent().build();
    }
}
