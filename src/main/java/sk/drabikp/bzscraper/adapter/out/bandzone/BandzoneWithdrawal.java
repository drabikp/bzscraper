package sk.drabikp.bzscraper.adapter.out.bandzone;

import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;

import java.util.List;
import java.util.Optional;

/**
 * Cancels or deletes a Bandzone concert by its id through the band-admin delete tab, one at
 * a time. Takes every concert, past ones included.
 */
abstract class BandzoneWithdrawal implements SyncStep {

    private final BandzonePortalClient portalClient;

    BandzoneWithdrawal(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    abstract void withdraw(BandzoneSession session, String bandzoneId) throws BandzoneUploadException;

    @Override
    public Platform platform() {
        return Platform.BANDZONE;
    }

    @Override
    public int batchSize() {
        return 1;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return Optional.empty();
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        return items.stream().map(this::withdraw).toList();
    }

    private StepOutcome withdraw(Item item) {
        try (BandzoneSession session = portalClient.openSession()) {
            withdraw(session, item.externalRef());
            return StepOutcome.done(null);
        } catch (BandzoneUploadException e) {
            return e.permanent() ? StepOutcome.failedForGood(e.getMessage()) : StepOutcome.failed(e.getMessage());
        }
    }
}
