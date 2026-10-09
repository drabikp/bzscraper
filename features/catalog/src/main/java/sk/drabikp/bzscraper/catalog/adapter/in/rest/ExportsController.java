package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.ExportsApi;
import sk.drabikp.bzscraper.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

/** Serves the per-platform file downloads (catalog.yaml, Exports). */
@RestController
class ExportsController implements ExportsApi {

    private final Platforms platforms;
    private final ExportGigsUseCase export;

    ExportsController(Platforms platforms, ExportGigsUseCase export) {
        this.platforms = platforms;
        this.export = export;
    }

    @Override
    public ResponseEntity<Resource> exportGigs(String platform) {
        return CatalogMapping.download(export.export(platforms.get(platform)));
    }
}
