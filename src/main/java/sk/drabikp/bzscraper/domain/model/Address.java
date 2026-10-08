package sk.drabikp.bzscraper.domain.model;

/**
 * Value object: where exactly a gig is, beyond the town's name — what tells the platforms
 * which of several same-named towns is meant. Every part is optional; a town picked from
 * the place search gives district, region and coordinates (the town's centre).
 */
public record Address(String street, String postalCode, String district, String region,
                      Double latitude, Double longitude) {

    public Address {
        street = blankToNull(street);
        postalCode = blankToNull(postalCode);
        district = blankToNull(district);
        region = blankToNull(region);
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("coordinates need both latitude and longitude");
        }
    }

    /** The town was picked from the place search (not just typed). */
    public boolean resolved() {
        return district != null && latitude != null;
    }

    /** Great-circle distance in km to the given point, or null without coordinates. */
    public Double kilometresTo(double lat, double lon) {
        if (latitude == null) {
            return null;
        }
        double dLat = Math.toRadians(lat - latitude);
        double dLon = Math.toRadians(lon - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latitude))
                * Math.cos(Math.toRadians(lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
