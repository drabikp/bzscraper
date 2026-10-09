package sk.drabikp.bzscraper.sync.domain;

/** What a sync task does to a gig's copy on one platform. */
public enum SyncAction {
    PUBLISH,
    UPDATE,
    CANCEL,
    DELETE,
    /** Replace the cancelled copy with an active one (delete + publish again). */
    REACTIVATE;

    /**
     * Safe to repeat after a failure that leaves it unclear what happened: doing it twice
     * changes nothing. Publishing (and reactivating, which publishes) twice would create a
     * second event on the platform, so those are retried only by the user.
     */
    public boolean repeatable() {
        return this == UPDATE || this == CANCEL || this == DELETE;
    }

    public String verb() {
        return switch (this) {
            case PUBLISH -> "publish";
            case UPDATE -> "update";
            case CANCEL -> "cancel";
            case DELETE -> "delete";
            case REACTIVATE -> "reactivate";
        };
    }
}
