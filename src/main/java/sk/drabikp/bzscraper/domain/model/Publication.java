package sk.drabikp.bzscraper.domain.model;

/**
 * A catalog gig's copy on one platform: the platform's id for it ({@code externalRef}),
 * or null when the platform gave none back.
 */
public record Publication(Platform platform, GigId gigId, String externalRef) {

    public boolean hasExternalRef() {
        return externalRef != null && !externalRef.isBlank();
    }
}
