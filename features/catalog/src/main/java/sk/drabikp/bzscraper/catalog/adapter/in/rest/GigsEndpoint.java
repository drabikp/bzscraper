package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import sk.drabikp.bzscraper.catalog.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.application.InvalidGigException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigDraft;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.sync.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.ResyncGigUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The catalog over HTTP: its gigs with their state on every platform, adding, editing,
 * cancelling and deleting them (the platforms follow through the sync), publishing and
 * re-syncing. An edit names the revision it was made on ({@link GigJson#rev()}).
 */
@RestController
@RequestMapping("/api/gigs")
class GigsEndpoint {

    /** How much sync history a gig's page shows. */
    private static final int HISTORY = 500;

    private final ListGigsUseCase listGigs;
    private final ListPublicationsUseCase publications;
    private final SaveGigUseCase saveGig;
    private final UpdateGigUseCase updateGig;
    private final CancelGigUseCase cancelGig;
    private final DeleteGigUseCase deleteGig;
    private final PublishGigsUseCase publishGigs;
    private final ResyncGigUseCase resyncGig;
    private final SyncLogUseCase syncLog;
    private final Platforms platforms;
    private final Clock clock;
    private final LiveUpdates live;

    GigsEndpoint(ListGigsUseCase listGigs, ListPublicationsUseCase publications, SaveGigUseCase saveGig,
                 UpdateGigUseCase updateGig, CancelGigUseCase cancelGig, DeleteGigUseCase deleteGig,
                 PublishGigsUseCase publishGigs, ResyncGigUseCase resyncGig, SyncLogUseCase syncLog,
                 Platforms platforms, Clock clock, LiveUpdates live) {
        this.listGigs = listGigs;
        this.publications = publications;
        this.saveGig = saveGig;
        this.updateGig = updateGig;
        this.cancelGig = cancelGig;
        this.deleteGig = deleteGig;
        this.publishGigs = publishGigs;
        this.resyncGig = resyncGig;
        this.syncLog = syncLog;
        this.platforms = platforms;
        this.clock = clock;
        this.live = live;
    }

    record Edit(String rev, GigDraft gig) {
    }

    record Edited(GigJson gig, QueueJson queued) {
    }

    record Selection(List<String> gigs, List<String> platforms) {
    }

    record History(long taskId, String platform, String action, String status, String at, String message) {
    }

    record Detail(GigJson gig, List<History> history) {
    }

    @GetMapping
    List<GigJson> gigs() {
        Map<GigId, Map<Platform, Publication>> published = publications.publicationsByGig();
        List<SyncTask> open = syncLog.unfinished().stream().filter(t -> t.status().open()).toList();
        return listGigs.allGigs().stream()
                .sorted(Comparator.comparing(g -> g.schedule().start()))
                .map(g -> GigJson.of(g, published.getOrDefault(g.id(), Map.of()), open, platforms, clock))
                .toList();
    }

    @GetMapping("/{id}")
    Detail gig(@PathVariable String id) {
        Gig gig = find(id);
        List<History> history = syncLog.recent(HISTORY).stream().filter(t -> t.gigId().equals(gig.id()))
                .map(t -> new History(t.id(), t.platform().id(), t.action().name(), t.status().name(),
                        t.updatedAt().toString(), t.message()))
                .toList();
        return new Detail(json(gig), history);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GigJson add(@RequestBody GigDraft draft) {
        Gig gig = InvalidGigException.gigOf(draft);
        saveGig.add(gig);
        live.changed(LiveUpdates.Topic.GIGS);
        return json(gig);
    }

    @PutMapping("/{id}")
    Edited edit(@PathVariable String id, @RequestBody Edit edit) {
        Gig seen = find(id);
        if (!GigJson.rev(seen).equals(edit.rev())) {
            throw new ConcurrentChangeException(ConcurrentChangeException.CHANGED, "The gig was changed meanwhile "
                    + "(in another window, by an import or from the calendar) — nothing was changed. Reload it and "
                    + "edit again.");
        }
        Gig updated = InvalidGigException.gigOf(edit.gig());
        QueueResult queued = updateGig.update(seen, updated);
        live.changed(LiveUpdates.Topic.GIGS);
        return new Edited(json(updated), QueueJson.of(queued));
    }

    @PostMapping("/{id}/cancel")
    QueueJson cancel(@PathVariable String id) {
        return changed(cancelGig.cancel(find(id).id()));
    }

    @PostMapping("/{id}/reactivate")
    QueueJson reactivate(@PathVariable String id) {
        return changed(cancelGig.reactivate(find(id).id()));
    }

    @DeleteMapping("/{id}")
    QueueJson delete(@PathVariable String id) {
        return changed(deleteGig.delete(find(id).id()));
    }

    @PostMapping("/publish")
    QueueJson publish(@RequestBody Selection selection) {
        Set<Platform> targets = new LinkedHashSet<>();
        for (String platform : selection.platforms() == null ? List.<String>of() : selection.platforms()) {
            targets.add(platforms.find(platform).orElseThrow(() ->
                    new IllegalArgumentException("not a platform: " + platform)));
        }
        List<Gig> gigs = gigIds(selection).stream().map(this::find).toList();
        return changed(publishGigs.publish(targets, gigs));
    }

    @PostMapping("/resync")
    QueueJson resync(@RequestBody Selection selection) {
        return changed(resyncGig.resync(gigIds(selection).stream().map(id -> find(id).id()).toList()));
    }

    private static List<String> gigIds(Selection selection) {
        return selection.gigs() == null ? List.of() : selection.gigs();
    }

    private QueueJson changed(QueueResult result) {
        live.changed(LiveUpdates.Topic.GIGS);
        return QueueJson.of(result);
    }

    private Gig find(String token) {
        GigId id = GigId.fromToken(token);
        return listGigs.allGigs().stream().filter(g -> g.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such gig."));
    }

    private GigJson json(Gig gig) {
        List<SyncTask> open = syncLog.unfinished().stream().filter(t -> t.status().open()).toList();
        return GigJson.of(gig, publications.publicationsByGig().getOrDefault(gig.id(), Map.of()), open, platforms,
                clock);
    }
}
