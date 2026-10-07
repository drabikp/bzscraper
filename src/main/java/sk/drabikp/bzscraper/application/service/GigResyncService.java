package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.ResyncGigUseCase;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.GigUpdateException;
import sk.drabikp.bzscraper.application.port.out.GigUpdater;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformResult;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps platform copies of a published gig in step with the catalog.
 *
 * <p><b>Edit</b> — the platform's {@link GigUpdater} overwrites its copy in place, so the
 * external id stays. The published records already follow the gig to its current id:
 * the catalog update moves them when an edit changes the date or venue.
 *
 * <p><b>Reactivate</b> — platforms cannot un-cancel (Bandzone has no such action), so the
 * cancelled copy is <em>deleted, then re-created</em> with the {@link GigWithdrawer} and
 * {@link GigPublisher} strategies, and the record takes the new external id.
 */
public class GigResyncService implements ResyncGigUseCase {

    private final Map<Platform, GigPublisher> publishers = new EnumMap<>(Platform.class);
    private final Map<Platform, GigUpdater> updaters = new EnumMap<>(Platform.class);
    private final Map<Platform, GigWithdrawer> withdrawers = new EnumMap<>(Platform.class);
    private final PublishedGigStore publishedGigStore;

    public GigResyncService(List<GigPublisher> publishers, List<GigUpdater> updaters,
                            List<GigWithdrawer> withdrawers, PublishedGigStore publishedGigStore) {
        publishers.forEach(p -> this.publishers.put(p.platform(), p));
        updaters.forEach(u -> this.updaters.put(u.platform(), u));
        withdrawers.forEach(w -> this.withdrawers.put(w.platform(), w));
        this.publishedGigStore = publishedGigStore;
    }

    @Override
    public List<PlatformResult> pushEdit(Gig gig) {
        List<PlatformResult> results = new ArrayList<>();
        for (Platform platform : publishedPlatforms(gig.id())) {
            results.add(updateOn(platform, publishedGigStore.externalRef(platform, gig.id()), gig));
        }
        return results;
    }

    private PlatformResult updateOn(Platform platform, Optional<String> ref, Gig current) {
        GigUpdater updater = updaters.get(platform);
        if (updater == null || ref.isEmpty()) {
            return unsupported(platform, "Updating");
        }
        try {
            updater.update(ref.get(), current);
            return PlatformResult.ok(platform);
        } catch (GigUpdateException e) {
            return PlatformResult.failed(platform, e.getMessage());
        }
    }

    @Override
    public List<PlatformResult> reactivate(Gig gig) {
        List<PlatformResult> results = new ArrayList<>();
        for (Platform platform : publishedPlatforms(gig.id())) {
            results.add(recreateOn(platform, gig));
        }
        return results;
    }

    private PlatformResult recreateOn(Platform platform, Gig gig) {
        Optional<String> oldRef = publishedGigStore.externalRef(platform, gig.id());
        GigWithdrawer withdrawer = withdrawers.get(platform);
        GigPublisher publisher = publishers.get(platform);
        if (withdrawer == null || publisher == null || oldRef.isEmpty()) {
            return unsupported(platform, "Reactivating");
        }

        // Delete first: Bandzone would otherwise list two copies of the same gig.
        try {
            withdrawer.withdraw(oldRef.get(), WithdrawAction.DELETE);
        } catch (GigWithdrawalException e) {
            return PlatformResult.failed(platform, "Could not remove the cancelled copy: " + e.getMessage());
        }
        // The old copy is gone; from here on, no record means the next Publish re-creates it.
        publishedGigStore.remove(platform, gig.id());

        PublishResult created;
        try {
            created = publisher.publishNew(List.of(gig)).getFirst();
        } catch (RuntimeException e) {
            return removedButNotRecreated(platform, e.getMessage());
        }
        if (created.status() != PublishStatus.PUBLISHED) {
            return removedButNotRecreated(platform, created.detail());
        }
        publishedGigStore.record(platform, gig.id(), created.externalRef());
        return PlatformResult.ok(platform);
    }

    private List<Platform> publishedPlatforms(GigId id) {
        List<Platform> platforms = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            if (publishedGigStore.isPublished(platform, id)) {
                platforms.add(platform);
            }
        }
        return platforms;
    }

    private static PlatformResult unsupported(Platform platform, String operation) {
        return PlatformResult.failed(platform, operation + " published gigs on " + platform
                + " is not supported yet — update it there by hand.");
    }

    private static PlatformResult removedButNotRecreated(Platform platform, String reason) {
        return PlatformResult.failed(platform, "Cancelled copy removed, but re-creating failed ("
                + reason + ") — publish the gig again.");
    }
}
