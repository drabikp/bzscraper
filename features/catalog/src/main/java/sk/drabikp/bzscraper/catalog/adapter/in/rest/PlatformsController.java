package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformJson;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformsApi;
import sk.drabikp.bzscraper.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.util.List;

/** Serves what the pages need to know of the platforms (catalog.yaml, Platforms). */
@RestController
class PlatformsController implements PlatformsApi {

    private final Platforms platforms;
    private final ExportGigsUseCase export;

    PlatformsController(Platforms platforms, ExportGigsUseCase export) {
        this.platforms = platforms;
        this.export = export;
    }

    @Override
    public ResponseEntity<List<PlatformJson>> listPlatforms() {
        return ResponseEntity.ok(CatalogMapping.toJson(platforms.traits(), export.exportable()));
    }
}
