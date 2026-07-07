package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;

/**
 * Placeholder {@link BandzonePortalClient}: lets the app boot and the Bandzone
 * publish pipeline wire without a browser. Opening a session fails with a clear
 * message, so a publish reports every gig FAILED rather than silently doing nothing.
 * Enable the real client with {@code bzscraper.bandzone.selenium.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.bandzone.selenium.enabled", havingValue = "false", matchIfMissing = true)
public class StubBandzonePortalClient implements BandzonePortalClient {

    private static final Logger logger = LoggerFactory.getLogger(StubBandzonePortalClient.class);

    @Override
    public BandzoneSession openSession() throws BandzoneUploadException {
        logger.warn("StubBandzonePortalClient: openSession requested but no real portal client "
                + "is wired. Set bzscraper.bandzone.selenium.enabled=true to publish for real.");
        throw new BandzoneUploadException(
                "Bandzone publishing is not enabled (set bzscraper.bandzone.selenium.enabled=true "
                        + "and configure credentials).");
    }
}
