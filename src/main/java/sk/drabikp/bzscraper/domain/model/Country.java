package sk.drabikp.bzscraper.domain.model;

/**
 * A country a gig can take place in. Carries the Bandsintown-canonical display
 * name and an IANA timezone, both required by the BIT CSV import. Scoped to the
 * band's actual markets; extend as needed.
 */
public enum Country {
    CZECHIA("Czechia", "Europe/Prague"),
    SLOVAKIA("Slovakia", "Europe/Bratislava");

    private final String displayName;
    private final String timezone;

    Country(String displayName, String timezone) {
        this.displayName = displayName;
        this.timezone = timezone;
    }

    /** Bandsintown-canonical country name (e.g. "Czechia", not "Czech Republic"). */
    public String displayName() {
        return displayName;
    }

    /** IANA timezone id (e.g. "Europe/Prague"). */
    public String timezone() {
        return timezone;
    }
}
