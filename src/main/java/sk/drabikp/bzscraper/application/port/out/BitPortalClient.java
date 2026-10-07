package sk.drabikp.bzscraper.application.port.out;

/**
 * Outbound port for the Bandsintown artist portal (Bandsintown has no public write
 * API). A whole batch shares one authenticated {@link BitSession}; the browser
 * automation (login + authenticator code) stays at the edge so the use cases remain
 * testable with mocks.
 */
public interface BitPortalClient {

    /**
     * Logs in (or reuses a still-valid login) and opens a session.
     *
     * @throws BitUploadException if login fails (the whole batch then fails)
     */
    BitSession openSession() throws BitUploadException;
}
