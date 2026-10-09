package sk.drabikp.bzscraper.check.application;

import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.application.port.in.StartPlatformCheckUseCase;

import java.util.concurrent.Executor;

/**
 * A check the user started: runs on the {@code executor} so the request doesn't wait for the
 * platforms; the check itself announces its start and end on the live updates.
 */
public class BackgroundCheck implements StartPlatformCheckUseCase {

    private static final System.Logger log = System.getLogger(BackgroundCheck.class.getName());

    private final CheckPlatformsUseCase check;
    private final Executor executor;

    public BackgroundCheck(CheckPlatformsUseCase check, Executor executor) {
        this.check = check;
        this.executor = executor;
    }

    @Override
    public void start() {
        if (check.running()) {
            return;
        }
        executor.execute(() -> {
            try {
                check.check();
            } catch (RuntimeException e) {
                log.log(System.Logger.Level.ERROR, "The platform check failed", e);
            }
        });
    }
}
