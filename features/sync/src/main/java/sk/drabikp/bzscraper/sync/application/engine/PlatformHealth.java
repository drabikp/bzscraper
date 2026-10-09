package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Set;

/** How each platform has been answering lately — the engine reports, and skips the ones held back. */
public interface PlatformHealth {

    /** The platforms whose work is held back right now. */
    Set<Platform> held();

    boolean held(Platform platform);

    /** The platform answered (any result other than a temporary failure). */
    void succeeded(Platform platform);

    /** A batch on the platform failed as a whole. */
    void failed(Platform platform, String failure);
}
