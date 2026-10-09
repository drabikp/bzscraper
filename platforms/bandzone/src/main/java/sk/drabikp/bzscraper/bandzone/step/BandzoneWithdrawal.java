package sk.drabikp.bzscraper.bandzone.step;

import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;

/**
 * Cancels or deletes a Bandzone concert by its id through the band-admin delete tab, one at
 * a time. Takes every concert, past ones included.
 */
abstract class BandzoneWithdrawal implements OneAtATimeStep {

    private final BandzonePortalClient portalClient;

    BandzoneWithdrawal(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    abstract void withdraw(BandzoneSession session, String bandzoneId) throws BandzoneUploadException;

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public StepOutcome runOne(Item item) {
        try (BandzoneSession session = portalClient.openSession()) {
            withdraw(session, item.externalRef());
            return StepOutcome.done(null);
        } catch (BandzoneUploadException e) {
            return e.outcome();
        }
    }
}
