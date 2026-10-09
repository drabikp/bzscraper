package sk.drabikp.bzscraper.check.adapter.in.schedule;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.config.CheckProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reads the platforms and compares them with the catalog once a night
 * ({@code bzscraper.check.cron}, default 04:30), so drift is found without anyone looking —
 * read-only. On its own thread, like the sync worker; off with
 * {@code bzscraper.check.enabled=false} (tests).
 */
@Component
class PlatformCheckSchedule {

    private static final Logger log = LoggerFactory.getLogger(PlatformCheckSchedule.class);

    private final CheckPlatformsUseCase platformCheck;
    private final boolean enabled;
    private final Clock clock;
    private final CronExpression cron;
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "platform-check");
        t.setDaemon(true);
        return t;
    });

    public PlatformCheckSchedule(CheckPlatformsUseCase platformCheck, CheckProperties properties, Clock clock) {
        this.platformCheck = platformCheck;
        this.enabled = properties.enabled();
        this.cron = CronExpression.parse(properties.cron());
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        if (enabled) {
            scheduleNext();
        }
    }

    @PreDestroy
    void stop() {
        thread.shutdownNow();
    }

    private void scheduleNext() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime next = cron.next(now);
        if (next != null) {
            thread.schedule(this::run, Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void run() {
        try {
            platformCheck.check().ifPresent(check -> log.info("Platform check: {} difference(s), unreadable: {}",
                    check.drifts().size(), check.unreadable().keySet()));
        } catch (RuntimeException e) {
            log.warn("The nightly platform check failed", e);
        } finally {
            scheduleNext();
        }
    }
}
