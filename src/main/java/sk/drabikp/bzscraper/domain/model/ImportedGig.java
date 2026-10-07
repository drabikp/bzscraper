package sk.drabikp.bzscraper.domain.model;

/**
 * A gig as it exists on a platform, read for import: the platform's version of the gig
 * and the platform's id for it ({@code externalRef}), so importing can link the catalog
 * gig to that existing event instead of publishing a duplicate.
 */
public record ImportedGig(Platform platform, Gig gig, String externalRef) {

    public ImportedGig {
        if (platform == null || gig == null || externalRef == null || externalRef.isBlank()) {
            throw new IllegalArgumentException("an imported gig needs its platform, gig and platform id");
        }
    }
}
