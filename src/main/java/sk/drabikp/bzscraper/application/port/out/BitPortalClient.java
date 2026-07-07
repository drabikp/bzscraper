package sk.drabikp.bzscraper.application.port.out;

/**
 * Outbound port for the Bandsintown artist-portal interaction. Keeps the browser
 * automation (Selenium + 2FA) at the edge so the publish use case stays testable
 * with a mock. The implementation logs into the portal and uploads the given CSV
 * through the bulk-import screen.
 */
public interface BitPortalClient {

    /**
     * Uploads a Bandsintown bulk-import CSV to the artist portal.
     *
     * @param csv the full CSV content (header + rows) produced by the exporter
     * @throws BitUploadException if login, upload, or import fails
     */
    void uploadCsv(String csv) throws BitUploadException;
}
