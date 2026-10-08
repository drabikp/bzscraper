package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Platform;

import java.time.Instant;
import java.util.List;

/**
 * The per-platform circuit breakers of the sync: after several failures in a row on a
 * platform (its portal changed, the login broke, it is down), that platform's work is held
 * back for a while instead of failing again and again — the rest of the sync goes on.
 */
public interface PlatformBreakerUseCase {

    /** The platforms whose breaker is not plainly closed (failing, held back, or on trial). */
    List<Breaker> breakers();

    /** Lets the platform's work run again now (the user fixed the cause). */
    void resume(Platform platform);

    /**
     * {@code failures} in a row; {@code heldUntil} non-null while the platform is held back —
     * once it has passed, the next batch is a trial ({@code trial}): success lets the work flow
     * again, a failure holds it back again.
     */
    record Breaker(Platform platform, int failures, Instant heldUntil, boolean trial, String lastFailure) {

        public boolean held(Instant now) {
            return heldUntil != null && now.isBefore(heldUntil);
        }
    }
}
