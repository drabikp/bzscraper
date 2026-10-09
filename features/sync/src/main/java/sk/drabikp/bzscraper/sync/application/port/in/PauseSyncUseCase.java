package sk.drabikp.bzscraper.sync.application.port.in;

/**
 * Pausing the platform sync: the gig in progress finishes, nothing new starts — the waiting
 * work stays queued — until resumed. Not kept over a restart.
 */
public interface PauseSyncUseCase {

    void pause();

    void resume();

    boolean paused();
}
