package sk.drabikp.bzscraper.importing.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportApi;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportApplyJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportReadJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportResultJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportStateJson;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase;

/** Serves the import (import.yaml): the platforms are read in the background, the plan applied by index. */
@RestController
class ImportController implements ImportApi {

    private final ImportSessionUseCase session;
    private final Platforms platforms;

    ImportController(ImportSessionUseCase session, Platforms platforms) {
        this.session = session;
        this.platforms = platforms;
    }

    @Override
    public ResponseEntity<ImportStateJson> importState() {
        return ResponseEntity.ok(ImportMapping.toJson(session.state()));
    }

    @Override
    public ResponseEntity<Void> readForImport(ImportReadJson read) {
        session.read(platforms.get(read.getPlatforms()));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<ImportResultJson> applyImport(ImportApplyJson apply) {
        return ResponseEntity.ok(ImportMapping.toJson(session.apply(ImportMapping.choices(apply))));
    }
}
