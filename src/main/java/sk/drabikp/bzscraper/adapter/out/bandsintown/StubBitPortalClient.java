package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;

/**
 * Used while Bandsintown automation is switched off, so the app boots without a
 * browser: every operation fails with a message saying how to switch it on.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandsintown.selenium.enabled", havingValue = "false", matchIfMissing = true)
public class StubBitPortalClient implements BitPortalClient {

    @Override
    public BitSession openSession() throws BitUploadException {
        throw new BitUploadException("Bandsintown publishing is switched off — set "
                + "bzscraper.bandsintown.selenium.enabled=true and the bzscraper.bandsintown.login/"
                + "password/totp-secret.", null, true);
    }
}
