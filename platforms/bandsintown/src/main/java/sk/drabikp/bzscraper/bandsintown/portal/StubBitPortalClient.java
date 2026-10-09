package sk.drabikp.bzscraper.bandsintown.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.BitUploadException;

import java.time.Duration;

/**
 * Used while Bandsintown automation is switched off, so the app boots without a
 * browser: every operation fails with a message saying how to switch it on.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandsintown.selenium.enabled", havingValue = "false", matchIfMissing = true)
class StubBitPortalClient implements BitPortalClient {

    @Override
    public BitSession openSession(Duration wait) throws BitUploadException {
        throw BitUploadException.needsUser("Bandsintown publishing is switched off — set "
                + "bzscraper.bandsintown.selenium.enabled=true and the bzscraper.bandsintown.login/"
                + "password/totp-secret.");
    }
}
