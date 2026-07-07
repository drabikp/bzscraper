package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.UploadedGigStore;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shared pre-flight for every platform: split the input into gigs already uploaded
 * (skipped) and gigs that must be published. Because {@link Gig} is an always-valid
 * aggregate, there is no "invalid gig" case to guard here — invalidity is rejected
 * at the mapping boundary, never reaching the pipeline.
 */
final class PublishPartitioner {

    private PublishPartitioner() {
    }

    record Partition(List<PublishResult> preResolved, List<Gig> toPublish) {
    }

    static Partition partition(Collection<Gig> gigs, Platform platform, UploadedGigStore store) {
        List<PublishResult> preResolved = new ArrayList<>();
        List<Gig> toPublish = new ArrayList<>();
        for (Gig gig : gigs) {
            if (store.isUploaded(platform, gig.id())) {
                preResolved.add(PublishResult.alreadyUploaded(platform, gig));
            } else {
                toPublish.add(gig);
            }
        }
        return new Partition(preResolved, toPublish);
    }
}
