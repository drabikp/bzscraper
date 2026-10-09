package sk.drabikp.bzscraper.check.application.port.in;

import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Optional;

/**
 * Reconciliation: reads what every platform shows (read-only) and compares it with the
 * catalog — the desired state — to find drift the sync can't know about: an event changed or
 * deleted on the platform by hand, or placed in a same-named town elsewhere. The user fixes a
 * drift with Re-sync, or forgets a link whose event is gone (Publish then creates it again).
 */
public interface CheckPlatformsUseCase {

    /** Reads the platforms now (slow: a browser for some); empty when a check is already running. */
    Optional<PlatformCheck> check();

    /** The latest check's result (kept in memory). */
    Optional<PlatformCheck> lastCheck();

    boolean running();


    /** Forgets that the gig is on the platform — its event is gone there; Publish creates it again. */
    void forget(Platform platform, GigId gigId);

    /** Takes a drift off the latest result (the user acted on it, e.g. queued a Re-sync). */
    void dismiss(Platform platform, GigId gigId);
}
