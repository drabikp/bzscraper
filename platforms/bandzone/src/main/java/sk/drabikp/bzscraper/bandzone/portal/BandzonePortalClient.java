package sk.drabikp.bzscraper.bandzone.portal;

import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;

import java.time.Duration;

/**
 * Outbound port for the Bandzone band-admin interaction. Bandzone has no bulk
 * import, so gigs are created one at a time through its 2-step JS wizard — but a
 * whole batch shares one authenticated {@link BandzoneSession} (one login), not one
 * login per gig. Keeps the browser automation at the edge so the publish use case
 * stays testable.
 */
public interface BandzonePortalClient {

    /**
     * For a sync step: logs in and opens a session; a browser busy with another operation is
     * not waited for ({@link FailureKind#BUSY} — the step is postponed).
     *
     * @throws BandzoneUploadException if login fails (the whole batch then fails)
     */
    default BandzoneSession openSession() throws BandzoneUploadException {
        return openSession(Duration.ZERO);
    }

    /** Like {@link #openSession()}, waiting up to {@code wait} for a busy browser. */
    BandzoneSession openSession(Duration wait) throws BandzoneUploadException;
}
