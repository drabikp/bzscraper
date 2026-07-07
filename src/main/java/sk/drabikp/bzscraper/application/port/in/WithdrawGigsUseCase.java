package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;
import sk.drabikp.bzscraper.domain.model.WithdrawResult;

import java.util.List;

/**
 * Cancels or deletes a gig on every platform it was published to (identified by the
 * external id kept in the published-gig store). Platforms where the gig was never
 * published are skipped. Returns one result per platform actually contacted.
 */
public interface WithdrawGigsUseCase {

    List<WithdrawResult> withdraw(GigId gigId, WithdrawAction action);
}
