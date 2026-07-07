package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.UploadedGigStore;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
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
 * Orchestrates publishing across every platform. Owns the shared skeleton — dedup
 * partition, dispatch to the right {@link GigPublisher}, mark-on-success, assemble —
 * so platform strategies only implement the push. A new platform is added by
 * registering one more {@code GigPublisher} bean; this class does not change.
 *
 * <pre>
 *   for each target platform:
 *     partition(gigs) -> preResolved (invalid / already-uploaded) + toPublish
 *     publisher.publishNew(toPublish) -> per-gig PUBLISHED/FAILED
 *     mark the PUBLISHED ones (only those the platform confirmed)
 * </pre>
 */
public class GigPublishingService implements PublishGigsUseCase {

    private final Map<Platform, GigPublisher> publishers;
    private final UploadedGigStore uploadedGigStore;

    public GigPublishingService(List<GigPublisher> publishers, UploadedGigStore uploadedGigStore) {
        this.publishers = new EnumMap<>(Platform.class);
        for (GigPublisher publisher : publishers) {
            GigPublisher existing = this.publishers.put(publisher.platform(), publisher);
            if (existing != null) {
                throw new IllegalStateException("Two GigPublishers registered for platform "
                        + publisher.platform());
            }
        }
        this.uploadedGigStore = uploadedGigStore;
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
                PublishPartitioner.partition(gigs, platform, uploadedGigStore);
        List<PublishResult> results = new ArrayList<>(partition.preResolved());
        if (partition.toPublish().isEmpty()) {
            return results;
        }

        List<PublishResult> pushed;
        try {
            pushed = publisher.publishNew(partition.toPublish());
        } catch (RuntimeException e) {
            // A publisher throwing an unexpected error fails only its own platform,
            // never the whole multi-platform run. Nothing is marked, so it retries.
            for (Gig gig : partition.toPublish()) {
                results.add(PublishResult.failed(platform, gig, e.getMessage()));
            }
            return results;
        }

        results.addAll(pushed);
        List<GigId> succeeded = pushed.stream()
                .filter(r -> r.status() == PublishStatus.PUBLISHED)
                .map(r -> r.gig().id())
                .toList();
        if (!succeeded.isEmpty()) {
            uploadedGigStore.markUploaded(platform, succeeded);
        }
        return results;
    }
}
