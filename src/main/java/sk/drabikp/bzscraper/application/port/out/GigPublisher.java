package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;

import java.util.List;

/**
 * Strategy for pushing gigs to one platform. The orchestrator handles the shared
 * skeleton (dedup, mark-on-success, result assembly) and calls {@link #publishNew}
 * with only the gigs that are new and valid — the publisher just performs the
 * platform-specific push (BIT: one CSV batch; BZ: per-gig wizard) and reports one
 * {@link PublishResult} per gig. Publishers do NOT touch the uploaded-set store.
 *
 * Adding a platform = one new {@code @Component} implementing this interface; the
 * orchestrator, store, and UI are untouched.
 */
public interface GigPublisher {

    Platform platform();

    /**
     * Publishes the given already-filtered gigs. Returns one result per input gig,
     * each tagged with {@link #platform()} and status PUBLISHED or FAILED.
     */
    List<PublishResult> publishNew(List<Gig> gigs);

    /** Whether the platform takes gigs that are already over (e.g. for the band's history). */
    boolean publishesPastEvents();
}
