package sk.drabikp.bzscraper.adapter.in.web;

import sk.drabikp.bzscraper.domain.model.Platform;

/** How platforms are named on the pages. */
final class PublishSummaries {

    private PublishSummaries() {
    }

    static String label(Platform platform) {
        return switch (platform) {
            case BANDZONE -> "Bandzone";
            case BANDSINTOWN -> "Bandsintown";
        };
    }
}
