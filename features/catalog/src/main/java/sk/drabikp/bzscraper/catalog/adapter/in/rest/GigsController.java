package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigDetailJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigEditJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigEditedJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigsApi;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.QueueResultJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.SelectionJson;
import sk.drabikp.bzscraper.catalog.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.gig.api.GigDraftJson;
import sk.drabikp.bzscraper.gig.api.GigDraftMapping;
import sk.drabikp.bzscraper.gig.application.InvalidGigException;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.ResyncGigUseCase;

import java.util.LinkedHashSet;
import java.util.List;

/** Serves the catalog's gigs (catalog.yaml, Gigs): the platforms follow every change through the sync. */
@RestController
class GigsController implements GigsApi {

    private final ListGigsUseCase gigs;
    private final SaveGigUseCase saveGig;
    private final UpdateGigUseCase updateGig;
    private final CancelGigUseCase cancelGig;
    private final DeleteGigUseCase deleteGig;
    private final PublishGigsUseCase publishGigs;
    private final ResyncGigUseCase resyncGigs;
    private final Platforms platforms;
    private final GigViews views;

    GigsController(ListGigsUseCase gigs, SaveGigUseCase saveGig, UpdateGigUseCase updateGig,
                   CancelGigUseCase cancelGig, DeleteGigUseCase deleteGig, PublishGigsUseCase publishGigs,
                   ResyncGigUseCase resyncGigs, Platforms platforms, GigViews views) {
        this.gigs = gigs;
        this.saveGig = saveGig;
        this.updateGig = updateGig;
        this.cancelGig = cancelGig;
        this.deleteGig = deleteGig;
        this.publishGigs = publishGigs;
        this.resyncGigs = resyncGigs;
        this.platforms = platforms;
        this.views = views;
    }

    @Override
    public ResponseEntity<List<GigJson>> listGigs() {
        return ResponseEntity.ok(views.all());
    }

    @Override
    public ResponseEntity<GigDetailJson> gigDetail(String id) {
        return ResponseEntity.ok(views.detail(gigs.gig(GigId.fromToken(id))));
    }

    @Override
    public ResponseEntity<GigJson> addGig(GigDraftJson draft) {
        Gig gig = InvalidGigException.gigOf(GigDraftMapping.toDraft(draft));
        saveGig.add(gig);
        return ResponseEntity.status(HttpStatus.CREATED).body(views.of(gig));
    }

    @Override
    public ResponseEntity<GigEditedJson> editGig(String id, GigEditJson edit) {
        Gig seen = GigRevisions.asSeen(gigs.gig(GigId.fromToken(id)), edit.getRev());
        Gig updated = InvalidGigException.gigOf(GigDraftMapping.toDraft(edit.getGig()));
        QueueResultJson queued = CatalogMapping.toJson(updateGig.update(seen, updated));
        return ResponseEntity.ok(new GigEditedJson(views.of(updated), queued));
    }

    @Override
    public ResponseEntity<QueueResultJson> deleteGig(String id) {
        return ResponseEntity.ok(CatalogMapping.toJson(deleteGig.delete(gigs.gig(GigId.fromToken(id)).id())));
    }

    @Override
    public ResponseEntity<QueueResultJson> cancelGig(String id) {
        return ResponseEntity.ok(CatalogMapping.toJson(cancelGig.cancel(gigs.gig(GigId.fromToken(id)).id())));
    }

    @Override
    public ResponseEntity<QueueResultJson> reactivateGig(String id) {
        return ResponseEntity.ok(CatalogMapping.toJson(cancelGig.reactivate(gigs.gig(GigId.fromToken(id)).id())));
    }

    @Override
    public ResponseEntity<QueueResultJson> publishGigs(SelectionJson selection) {
        List<Gig> selected = CatalogMapping.gigIds(selection).stream().map(gigs::gig).toList();
        return ResponseEntity.ok(CatalogMapping.toJson(publishGigs.publish(
                new LinkedHashSet<>(platforms.get(selection.getPlatforms())), selected)));
    }

    @Override
    public ResponseEntity<QueueResultJson> resyncGigs(SelectionJson selection) {
        List<GigId> selected = CatalogMapping.gigIds(selection).stream().map(id -> gigs.gig(id).id()).toList();
        return ResponseEntity.ok(CatalogMapping.toJson(resyncGigs.resync(selected)));
    }
}
