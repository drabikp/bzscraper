package sk.drabikp.bzscraper.adapter.out.bandzone;

import sk.drabikp.bzscraper.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;

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
