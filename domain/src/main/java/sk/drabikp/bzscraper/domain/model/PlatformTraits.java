package sk.drabikp.bzscraper.domain.model;

/**
 * What a platform is like, as far as the core needs to know — told by the platform's adapter.
 *
 * @param displayName          how people write its name
 * @param keepsCancelledEvents a cancelled gig stays listed there as cancelled (otherwise
 *                             cancelling removes it)
 * @param listsBandSlot        it lists when the band plays (the band's slot of a longer event)
 *                             rather than the whole event
 * @param carriesAdmission     it shows the entry (fee or free)
 * @param importPrecedence     whose details win on import when versions differ: lower first
 *                             (the catalog's always win)
 * @param eventUrlTemplate     the public page of an event, {@code {id}} standing for its id
 */
public record PlatformTraits(Platform platform, String displayName, boolean keepsCancelledEvents,
                             boolean listsBandSlot, boolean carriesAdmission, int importPrecedence,
                             String eventUrlTemplate) {

    public PlatformTraits {
        if (platform == null || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("a platform needs its id and name");
        }
    }

    /** The event's public page, or null when the platform has none. */
    public String eventUrl(String externalRef) {
        return eventUrlTemplate == null || externalRef == null || externalRef.isBlank() ? null
                : eventUrlTemplate.replace("{id}", externalRef);
    }
}
