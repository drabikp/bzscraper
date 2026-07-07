package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.WithdrawGigsUseCase;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;
import sk.drabikp.bzscraper.domain.model.WithdrawResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Withdraws (cancels/deletes) a gig from every platform it was published to. For each
 * platform with a stored external id, drives the platform's {@link GigWithdrawer};
 * on a successful DELETE it forgets the gig in the store. Skips platforms where the
 * gig was never published. One failing platform does not stop the others.
 */
public class GigWithdrawalService implements WithdrawGigsUseCase {

    private final Map<Platform, GigWithdrawer> withdrawers;
    private final PublishedGigStore publishedGigStore;

    public GigWithdrawalService(List<GigWithdrawer> withdrawers, PublishedGigStore publishedGigStore) {
        this.withdrawers = new EnumMap<>(Platform.class);
        for (GigWithdrawer withdrawer : withdrawers) {
            GigWithdrawer existing = this.withdrawers.put(withdrawer.platform(), withdrawer);
            if (existing != null) {
                throw new IllegalStateException("Two GigWithdrawers registered for platform "
                        + withdrawer.platform());
            }
        }
        this.publishedGigStore = publishedGigStore;
    }

    @Override
    public List<WithdrawResult> withdraw(GigId gigId, WithdrawAction action) {
        List<WithdrawResult> results = new ArrayList<>();
        for (Map.Entry<Platform, GigWithdrawer> entry : withdrawers.entrySet()) {
            Platform platform = entry.getKey();
            Optional<String> externalRef = publishedGigStore.externalRef(platform, gigId);
            if (externalRef.isEmpty()) {
                continue; // never published there (or no id captured)
            }
            try {
                entry.getValue().withdraw(externalRef.get(), action);
                if (action == WithdrawAction.DELETE) {
                    publishedGigStore.remove(platform, gigId);
                }
                results.add(WithdrawResult.ok(platform));
            } catch (GigWithdrawalException e) {
                results.add(WithdrawResult.failed(platform, e.getMessage()));
            }
        }
        return results;
    }
}
