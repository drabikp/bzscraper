package sk.drabikp.bzscraper.adapter.in.web;

import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;

/**
 * How a gig's copy on a platform is shown in the catalog: the platform's name, linking
 * to the public page when the platform's id is known. Bandsintown has no cancelled
 * state — cancelling removed the event there, so a cancelled gig gets no link.
 */
final class PlatformLinks {

    /** {@code href} is null when there is nothing to link to. */
    record Link(String text, String href) {
    }

    private PlatformLinks() {
    }

    static Link of(Publication publication, boolean gigCancelled) {
        Platform platform = publication.platform();
        String name = PublishSummaries.label(platform);
        if (platform == Platform.BANDSINTOWN && gigCancelled) {
            return new Link(name + " (removed)", null);
        }
        if (!publication.hasExternalRef()) {
            return new Link(name, null);
        }
        return new Link(name, url(platform, publication.externalRef()));
    }

    static String url(Platform platform, String externalRef) {
        return switch (platform) {
            case BANDZONE -> "https://bandzone.cz/koncert/" + externalRef;      // redirects to the full page
            case BANDSINTOWN -> "https://www.bandsintown.com/e/" + externalRef;
        };
    }
}
