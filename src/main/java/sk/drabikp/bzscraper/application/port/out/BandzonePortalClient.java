package sk.drabikp.bzscraper.application.port.out;

/**
 * Outbound port for the Bandzone band-admin interaction. Bandzone has no bulk
 * import, so gigs are created one at a time through its 2-step JS wizard — but a
 * whole batch shares one authenticated {@link BandzoneSession} (one login), not one
 * login per gig. Keeps the browser automation at the edge so the publish use case
 * stays testable.
 */
public interface BandzonePortalClient {

    /**
     * Logs in and opens a session for creating gigs.
     *
     * @throws BandzoneUploadException if login fails (the whole batch then fails)
     */
    BandzoneSession openSession() throws BandzoneUploadException;
}
