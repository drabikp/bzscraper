package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

/**
 * Cancels or deletes a previously published gig on one platform, addressed by the
 * platform's external id. One implementation per platform that supports withdrawal;
 * the withdrawal use case collects them via an injected {@code List}.
 */
public interface GigWithdrawer {

    Platform platform();

    void withdraw(String externalRef, WithdrawAction action) throws GigWithdrawalException;
}
