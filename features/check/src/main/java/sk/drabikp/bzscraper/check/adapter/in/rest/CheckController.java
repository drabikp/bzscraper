package sk.drabikp.bzscraper.check.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.CheckApi;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.CheckStateJson;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.DriftRefJson;
import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.application.port.in.StartPlatformCheckUseCase;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

/** Serves the platform check (check.yaml). */
@RestController
class CheckController implements CheckApi {

    private final CheckPlatformsUseCase check;
    private final StartPlatformCheckUseCase start;
    private final Platforms platforms;

    CheckController(CheckPlatformsUseCase check, StartPlatformCheckUseCase start, Platforms platforms) {
        this.check = check;
        this.start = start;
        this.platforms = platforms;
    }

    @Override
    public ResponseEntity<CheckStateJson> checkState() {
        return ResponseEntity.ok(CheckMapping.toJson(check.running(), check.lastCheck()));
    }

    @Override
    public ResponseEntity<Void> runCheck() {
        start.start();
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> forgetLink(DriftRefJson ref) {
        check.forget(platforms.get(ref.getPlatform()), GigId.fromToken(ref.getGig()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> dismissDrift(DriftRefJson ref) {
        check.dismiss(platforms.get(ref.getPlatform()), GigId.fromToken(ref.getGig()));
        return ResponseEntity.noContent().build();
    }
}
