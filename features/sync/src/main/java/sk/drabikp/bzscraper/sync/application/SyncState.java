package sk.drabikp.bzscraper.sync.application;

import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.engine.PlatformHealth;
import sk.drabikp.bzscraper.sync.application.port.in.SyncStateUseCase;
import sk.drabikp.bzscraper.sync.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.Set;
import java.util.stream.Collectors;

public class SyncState implements SyncStateUseCase {

    private final SyncOutbox outbox;
    private final PlatformHealth health;

    public SyncState(SyncOutbox outbox, PlatformHealth health) {
        this.outbox = outbox;
        this.health = health;
    }

    @Override
    public boolean busy(GigId gigId) {
        return outbox.openFor(gigId).stream().anyMatch(t -> t.status() == SyncStatus.RUNNING);
    }

    @Override
    public Set<GigId> gigsWithOpenWork() {
        return outbox.unfinished().stream().filter(t -> t.status().open()).map(SyncTask::gigId)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean heldBack(Platform platform) {
        return health.held(platform);
    }
}
