package sk.drabikp.bzscraper.domain.model;

/** Where a sync task is. */
public enum SyncStatus {
    /** Waiting to run (first time, or for its next retry). */
    PENDING,
    RUNNING,
    DONE,
    /** Gave up — needs the user: Retry or Discard. */
    FAILED,
    /** Dropped: superseded by a later change, or discarded by the user. */
    DISCARDED;

    /** Not settled yet: the platform may still change. */
    public boolean open() {
        return this == PENDING || this == RUNNING;
    }
}
