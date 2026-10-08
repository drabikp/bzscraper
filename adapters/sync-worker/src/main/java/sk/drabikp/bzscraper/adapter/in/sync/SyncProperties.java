package sk.drabikp.bzscraper.adapter.in.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bzscraper.sync.*}: the worker ({@code worker.enabled}, off in tests: tasks queue but
 * never run; {@code poll-seconds}) and the breaker ({@code breaker.failures} batches failing in
 * a row hold a platform back {@code breaker.cooldown-minutes}).
 */
@ConfigurationProperties("bzscraper.sync")
public record SyncProperties(Worker worker, Long pollSeconds, Breaker breaker) {

    public record Worker(Boolean enabled) {

        public Worker {
            enabled = enabled == null || enabled;
        }
    }

    public record Breaker(Integer failures, Long cooldownMinutes) {

        public Breaker {
            failures = failures == null ? 3 : failures;
            cooldownMinutes = cooldownMinutes == null ? 30 : cooldownMinutes;
        }
    }

    public SyncProperties {
        worker = worker == null ? new Worker(null) : worker;
        pollSeconds = pollSeconds == null ? 15 : pollSeconds;
        breaker = breaker == null ? new Breaker(null, null) : breaker;
    }
}
