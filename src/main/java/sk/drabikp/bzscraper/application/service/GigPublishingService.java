package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates publishing across every platform. Owns the shared skeleton — dedup,
 * dispatch to the right {@link GigPublisher}, record-on-success (with the platform's
 * external id), assemble — so platform strategies only implement the push. A new
 * platform is one more {@code GigPublisher} bean; this class does not change.
 */
public class GigPublishingService implements PublishGigsUseCase {

    private final Map<Platform, GigPublisher> publishers;
    private final PublishedGigStore publishedGigStore;

    public GigPublishingService(List<GigPublisher> publishers, PublishedGigStore publishedGigStore) {
        this.publishers = new EnumMap<>(Platform.class);
        for (GigPublisher publisher : publishers) {
            GigPublisher existing = this.publishers.put(publisher.platform(), publisher);
            if (existing != null) {
                throw new IllegalStateException("Two GigPublishers registered for platform "
                        + publisher.platform());
            }
        }
        this.publishedGigStore = publishedGigStore;
    }

    @Override
    public List<PublishResult> publish(Set<Platform> targets, Collection<Gig> gigs) {
        List<PublishResult> all = new ArrayList<>();
        for (Platform platform : targets) {
            all.addAll(publishToPlatform(platform, gigs));
        }
        return all;
    }

    private List<PublishResult> publishToPlatform(Platform platform, Collection<Gig> gigs) {
        GigPublisher publisher = publishers.get(platform);
        if (publisher == null) {
            throw new IllegalArgumentException("No GigPublisher registered for platform " + platform);
        }

        PublishPartitioner.Partition partition =
                PublishPartitioner.partition(gigs, platform, publishedGigStore);
        List<PublishResult> results = new ArrayList<>(partition.preResolved());
        if (partition.toPublish().isEmpty()) {
            return results;
        }

        List<PublishResult> pushed;
        try {
            pushed = publisher.publishNew(partition.toPublish());
        } catch (RuntimeException e) {
            // A publisher throwing an unexpected error fails only its own platform,
            // never the whole multi-platform run. Nothing is recorded, so it retries.
            for (Gig gig : partition.toPublish()) {
                results.add(PublishResult.failed(platform, gig, e.getMessage()));
            }
            return results;
        }

        results.addAll(pushed);
        pushed.stream()
                .filter(r -> r.status() == PublishStatus.PUBLISHED)
                .forEach(r -> publishedGigStore.record(platform, r.gig().id(), r.externalRef()));
        return results;
    }
}
