package sk.drabikp.bzscraper.domain.model;

public class BandNotFoundException extends RuntimeException {
    private final String bandSlug;

    public BandNotFoundException(String bandSlug) {
        super("Band not found: " + bandSlug);
        this.bandSlug = bandSlug;
    }

    public String bandSlug() {
        return bandSlug;
    }
}
