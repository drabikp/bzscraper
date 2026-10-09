package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.QueueResultJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.QueuedTaskJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.SelectionJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.SyncActionJson;
import sk.drabikp.bzscraper.catalog.application.port.in.ExportGigsUseCase.ExportFile;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;
import sk.drabikp.bzscraper.sync.domain.QueueResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** The catalog's other answers and requests as the API has them (catalog.yaml). */
final class CatalogMapping {

    private CatalogMapping() {
    }

    /** What a change queued for the platforms, and what it left out. */
    static QueueResultJson toJson(QueueResult result) {
        return new QueueResultJson(result.queued().stream()
                .map(t -> new QueuedTaskJson(t.id(), t.platform().id(), SyncActionJson.fromValue(t.action().name())))
                .toList(), result.notQueued());
    }

    static List<PlatformJson> toJson(List<PlatformTraits> traits, List<Platform> exportable) {
        return traits.stream().map(t -> new PlatformJson(t.platform().id(), t.displayName(), t.keepsCancelledEvents(),
                t.listsBandSlot(), t.carriesAdmission(), exportable.contains(t.platform()))).toList();
    }

    /** The selected gigs (none for an empty selection). */
    static List<GigId> gigIds(SelectionJson selection) {
        return selection.getGigs() == null ? List.of() : selection.getGigs().stream().map(GigId::fromToken).toList();
    }

    /** A file to download, under its own name. */
    static ResponseEntity<Resource> download(ExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(new ByteArrayResource(file.content().getBytes(StandardCharsets.UTF_8)));
    }
}
