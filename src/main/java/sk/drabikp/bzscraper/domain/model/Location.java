package sk.drabikp.bzscraper.domain.model;

/**
 * Value object: where a gig happens. City is required; venue is optional (shown as
 * "TBA" until known); country is optional (null when unknown) and carries the
 * timezone/canonical name the platforms need.
 */
public record Location(String venue, String city, Country country) {

    public Location {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("gig city is required");
        }
        city = city.trim();
        venue = (venue == null || venue.isBlank() || venue.trim().equalsIgnoreCase("TBA")) ? null : venue.trim();
    }

    /** Venue for display/publishing; "TBA" when not yet known. */
    public String displayVenue() {
        return venue != null ? venue : "TBA";
    }

    /** Bandsintown-canonical country name, or empty when unknown. */
    public String countryName() {
        return country != null ? country.displayName() : "";
    }

    /** IANA timezone id, or empty when the country is unknown. */
    public String timezone() {
        return country != null ? country.timezone() : "";
    }
}
