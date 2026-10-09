package sk.drabikp.bzscraper.gig.application.port.out;

/**
 * Tells whoever shows the app's state (the open pages) that something changed, so they read it
 * again. Only WHAT changed travels ({@link Topic}); the data itself is read through the API.
 * How it is delivered (server-sent events today) is the implementing module's business.
 */
public interface LiveUpdates {

    void changed(Topic topic);

    /** What changed. */
    enum Topic {
        /** The catalog's gigs. */
        GIGS,
        /** Platform work: queued, running, done or failed; a breaker; the pause. */
        SYNC,
        /** A platform check started, ended or its result changed. */
        CHECK,
        /** Reading the platforms for an import started or ended. */
        IMPORT,
        /** The band calendar was read or a decision about an event changed. */
        CALENDAR
    }

    /** For tests and setups without pages. */
    LiveUpdates NONE = topic -> {
    };
}
