package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** What the pages need to know of the platforms, and the per-platform file downloads. */
@RestController
@RequestMapping("/api")
class CatalogMetaEndpoint {

    private final Platforms platforms;
    private final ExportGigsUseCase export;

    CatalogMetaEndpoint(Platforms platforms, ExportGigsUseCase export) {
        this.platforms = platforms;
        this.export = export;
    }

    /** A platform as its module describes it ({@link PlatformTraits}); {@code exportable}: a file download exists. */
    record PlatformJson(String id, String name, boolean keepsCancelledEvents, boolean listsBandSlot,
                        boolean carriesAdmission, boolean exportable) {
    }

    @GetMapping("/platforms")
    List<PlatformJson> platforms() {
        List<Platform> exportable = export.exportable();
        return platforms.traits().stream().map(t -> new PlatformJson(t.platform().id(), t.displayName(),
                t.keepsCancelledEvents(), t.listsBandSlot(), t.carriesAdmission(),
                exportable.contains(t.platform()))).toList();
    }

    @GetMapping("/exports/{platform}")
    ResponseEntity<byte[]> download(@PathVariable String platform) {
        ExportGigsUseCase.ExportFile file = export.export(platforms.find(platform).orElseThrow(() ->
                new IllegalArgumentException("not a platform: " + platform)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content().getBytes(StandardCharsets.UTF_8));
    }
}
