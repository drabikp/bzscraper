package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigDetailJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigHistoryJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformStateJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformStateNameJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.SyncActionJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.SyncTaskStatusJson;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.gig.api.GigDraftMapping;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigDraft;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gigs as the pages get them: the gig's form fields and, per platform, whether it is there and
 * what the sync is doing with it — the catalog, its publications and the open sync work put
 * together.
 */
@Component
class GigViews {

    /** How much sync history a gig's page shows. */
    private static final int HISTORY = 500;

    private final ListGigsUseCase gigs;
    private final ListPublicationsUseCase publications;
    private final SyncLogUseCase syncLog;
    private final Platforms platforms;
    private final Clock clock;

    GigViews(ListGigsUseCase gigs, ListPublicationsUseCase publications, SyncLogUseCase syncLog, Platforms platforms,
             Clock clock) {
        this.gigs = gigs;
        this.publications = publications;
        this.syncLog = syncLog;
        this.platforms = platforms;
        this.clock = clock;
    }

    /** Every gig, earliest first. */
    List<GigJson> all() {
        Map<GigId, Map<Platform, Publication>> published = publications.publicationsByGig();
        List<SyncTask> open = openTasks();
        return gigs.allGigs().stream()
                .sorted(Comparator.comparing(g -> g.schedule().start()))
                .map(g -> json(g, published.getOrDefault(g.id(), Map.of()), open))
                .toList();
    }

    GigJson of(Gig gig) {
        return json(gig, publications.publicationsByGig().getOrDefault(gig.id(), Map.of()), openTasks());
    }

    /** The gig with its sync history, newest first. */
    GigDetailJson detail(Gig gig) {
        List<GigHistoryJson> history = syncLog.recent(HISTORY).stream().filter(t -> t.gigId().equals(gig.id()))
                .map(t -> new GigHistoryJson(t.id(), t.platform().id(), SyncActionJson.fromValue(t.action().name()),
                        SyncTaskStatusJson.fromValue(t.status().name()), t.updatedAt(), t.message()))
                .toList();
        return new GigDetailJson(of(gig), history);
    }

    /** The work not finished yet: queued, running, or failed and waiting for the user. */
    private List<SyncTask> openTasks() {
        return syncLog.unfinished();
    }

    private GigJson json(Gig gig, Map<Platform, Publication> published, List<SyncTask> open) {
        List<PlatformStateJson> states = platforms.all().stream()
                .map(platform -> state(gig, platform, published.get(platform), open))
                .toList();
        return new GigJson(gig.id().token(), GigRevisions.of(gig), gig.isPast(clock),
                GigDraftMapping.toJson(GigDraft.of(gig)), states);
    }

    private PlatformStateJson state(Gig gig, Platform platform, Publication publication, List<SyncTask> open) {
        String ref = publication != null && publication.hasExternalRef() ? publication.externalRef() : null;
        String url = ref != null ? platforms.traits(platform).eventUrl(ref) : null;
        Optional<SyncTask> task = open.stream()
                .filter(t -> t.platform().equals(platform) && t.gigId().equals(gig.id()))
                .max(Comparator.comparingLong(SyncTask::id));
        if (task.isEmpty()) {
            return new PlatformStateJson(platform.id(),
                    publication != null ? PlatformStateNameJson.LIVE : PlatformStateNameJson.NONE, ref, url, null,
                    null, 0, null, null);
        }
        SyncTask t = task.get();
        PlatformStateNameJson state = t.status() == SyncStatus.FAILED ? PlatformStateNameJson.FAILED
                : t.status() == SyncStatus.RUNNING ? PlatformStateNameJson.RUNNING
                : t.retrying() ? PlatformStateNameJson.RETRYING : PlatformStateNameJson.QUEUED;
        return new PlatformStateJson(platform.id(), state, ref, url, t.id(),
                SyncActionJson.fromValue(t.action().name()), t.attempts(), t.nextAttemptAt(), t.message());
    }
}
