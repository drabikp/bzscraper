package sk.drabikp.bzscraper.domain.model;

/**
 * Value object: where a gig happens. City is required; venue is optional (shown as
 * "TBA" until known); country is optional (null when unknown) and carries the
 * timezone/canonical name the platforms need. {@code address} (optional) is what tells
 * same-named towns apart — district, region, postal code, coordinates — when the town
 * was picked from the place search.
 */
public record Location(String venue, String city, Country country, Address address) {

    public Location {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("gig city is required");
        }
        city = city.trim();
        venue = (venue == null || venue.isBlank() || venue.trim().equalsIgnoreCase("TBA")) ? null : venue.trim();
    }

    public Location(String venue, String city, Country country) {
        this(venue, city, country, null);
    }

    /** Venue for display/publishing; "TBA" when not yet known. */
    public String displayVenue() {
        return venue != null ? venue : "TBA";
    }

    /** The country's short English name, or empty when unknown. */
    public String countryName() {
        return country != null ? country.displayName() : "";
    }

    /** IANA timezone id, or empty when the country is unknown. */
    public String timezone() {
        return country != null ? country.timezone() : "";
    }

    /** The town's district ("okres Přerov"), when known. */
    public String district() {
        return address != null ? address.district() : null;
    }
}
