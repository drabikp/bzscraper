package sk.drabikp.bzscraper.check.adapter.in.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.domain.Drift;
import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The platform check over HTTP: the latest result, running a check (in the background — it
 * reads the platforms; the page is told when it starts and ends), and taking a difference off
 * the result (fixed with Re-sync, or a gone event's link forgotten).
 */
@RestController
@RequestMapping("/api/check")
class CheckEndpoint {

    private static final Logger log = LoggerFactory.getLogger(CheckEndpoint.class);

    private final CheckPlatformsUseCase check;
    private final Platforms platforms;
    private final TaskExecutor executor;

    CheckEndpoint(CheckPlatformsUseCase check, Platforms platforms, TaskExecutor executor) {
        this.check = check;
        this.platforms = platforms;
        this.executor = executor;
    }

    record StateJson(boolean running, ResultJson last) {
    }

    record ResultJson(String checkedAt, List<DriftJson> drifts, Map<String, String> unreadable,
                      Map<String, Integer> unlinked) {
    }

    record DriftJson(String platform, String gigId, String gigLabel, String externalRef, String kind,
                     List<Drift.Difference> differences) {
    }

    record DriftRef(String platform, String gig) {
    }

    @GetMapping
    StateJson state() {
        return new StateJson(check.running(), check.lastCheck().map(CheckEndpoint::json).orElse(null));
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void run() {
        if (check.running()) {
            return;
        }
        executor.execute(() -> {
            try {
                check.check();
            } catch (RuntimeException e) {
                log.error("The platform check failed", e);
            }
        });
    }

    @PostMapping("/forget")
    void forget(@RequestBody DriftRef ref) {
        check.forget(platform(ref), GigId.fromToken(ref.gig()));
    }

    @PostMapping("/dismiss")
    void dismiss(@RequestBody DriftRef ref) {
        check.dismiss(platform(ref), GigId.fromToken(ref.gig()));
    }

    private Platform platform(DriftRef ref) {
        return platforms.find(ref.platform()).orElseThrow(() ->
                new IllegalArgumentException("not a platform: " + ref.platform()));
    }

    private static ResultJson json(PlatformCheck result) {
        Map<String, String> unreadable = new TreeMap<>();
        result.unreadable().forEach((p, why) -> unreadable.put(p.id(), why));
        Map<String, Integer> unlinked = new TreeMap<>();
        result.unlinked().forEach((p, n) -> unlinked.put(p.id(), n));
        return new ResultJson(result.checkedAt().toString(), result.drifts().stream()
                .map(d -> new DriftJson(d.platform().id(), d.gigId().token(), d.gigLabel(), d.externalRef(),
                        d.kind().name(), d.differences()))
                .toList(), unreadable, unlinked);
    }
}
