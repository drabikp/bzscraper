package sk.drabikp.bzscraper.catalog.adapter.in.web;

import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;

/**
 * How a gig's copy on a platform is shown in the catalog: the platform's name, linking
 * to the public page when the platform's id is known. A platform that keeps no cancelled
 * events removed the event when the gig was cancelled, so there a cancelled gig gets no link.
 */
final class PlatformLinks {

    /** {@code href} is null when there is nothing to link to. */
    record Link(String text, String href) {
    }

    private PlatformLinks() {
    }

    /** The gig's copy on a platform; a cancelled gig is gone from a platform that has no cancelled state. */
    static Link of(PlatformTraits traits, Publication publication, boolean gigCancelled) {
        String name = traits.displayName();
        if (gigCancelled && !traits.keepsCancelledEvents()) {
            return new Link(name + " (removed)", null);
        }
        return new Link(name, traits.eventUrl(publication.externalRef()));
    }
}
