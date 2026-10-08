package sk.drabikp.bzscraper.domain.model;

/**
 * A town as a place search knows it — not just a name: town names repeat a lot (three
 * Hranice, two Czech villages and a Slovak city called Košice), so the district
 * ("okres Přerov") and region tell them apart. {@code postalCode} may be null.
 */
public record Town(String name, String district, String region, String postalCode, Country country,
                   double latitude, double longitude) {

    public Town {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("town name is required");
        }
        name = name.strip();
    }

    /** A town only typed, not picked from the place search: nothing but its name (and country). */
    public static Town typed(String name, Country country) {
        return new Town(name, null, null, null, country, Double.NaN, Double.NaN);
    }

    /** The town was picked from the place search: its district and coordinates are known. */
    public boolean resolved() {
        return district != null && !Double.isNaN(latitude);
    }

    /** The town a gig's location names (resolved when its address was). */
    public static Town of(Location location) {
        Address a = location.address();
        return a != null && a.resolved()
                ? new Town(location.city(), a.district(), a.region(), a.postalCode(), location.country(), a.latitude(),
                a.longitude())
                : typed(location.city(), location.country());
    }

    /** "Hranice — okres Přerov, Olomoucký kraj" */
    public String label() {
        StringBuilder text = new StringBuilder(name);
        if (district != null || region != null) {
            text.append(" — ").append(district != null ? district : "")
                    .append(district != null && region != null ? ", " : "").append(region != null ? region : "");
        }
        if (country == Country.SLOVAKIA) {
            text.append(", Slovensko");
        }
        return text.toString();
    }

    /** The gig's address in this town ({@code street}, {@code postalCodeOverride} as typed, may be null). */
    public Address address(String street, String postalCodeOverride) {
        String code = postalCodeOverride != null && !postalCodeOverride.isBlank() ? postalCodeOverride : postalCode;
        return resolved() ? new Address(street, code, district, region, latitude, longitude)
                : new Address(street, code, null, null, null, null);
    }
}
