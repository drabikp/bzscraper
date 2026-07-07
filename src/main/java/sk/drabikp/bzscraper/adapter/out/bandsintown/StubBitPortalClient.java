package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;

/**
 * Placeholder {@link BitPortalClient} so the application wires and the publish
 * pipeline (dedup, export, result mapping) works and is tested end to end — but
 * it does NOT actually upload.
 *
 * TODO: replace with a real Selenium implementation that logs into
 * https://www.artist.bandsintown.com (email + password + TOTP 2FA, from config —
 * NOT hardcoded), opens the bulk CSV import screen, uploads the CSV, and confirms
 * the import. The portal's import-screen selectors must first be captured from a
 * logged-in session (same reverse-engineering done for the Bandzone flow). Selenium
 * + otp-java also need re-adding to pom.xml (they were removed with the old BITTest).
 * On any auth step-up (email verification / "unusual login") or import error, throw
 * {@link BitUploadException} with a clear, user-facing message.
 */
@Component
public class StubBitPortalClient implements BitPortalClient {

    private static final Logger logger = LoggerFactory.getLogger(StubBitPortalClient.class);

    @Override
    public void uploadCsv(String csv) throws BitUploadException {
        logger.warn("StubBitPortalClient: upload requested for {} bytes of CSV but no real "
                + "portal client is wired. Not uploaded.", csv != null ? csv.length() : 0);
        throw new BitUploadException(
                "Bandsintown upload is not wired yet. Capture the artist-portal CSV-import "
                        + "flow and implement a Selenium BitPortalClient (see StubBitPortalClient TODO).");
    }
}
