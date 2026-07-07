package sk.drabikp.bzscraper.domain.model;

/**
 * How an imported gig relates to the local catalog, keyed by {@link GigId}.
 */
public enum ReconciliationOutcome {
    /** On the platform, not in the catalog — a candidate to add. */
    NEW,
    /** In both and identical — nothing to do. */
    MATCHED,
    /** In both but the data differs — needs a keep-local-or-take-imported decision. */
    CONFLICT,
    /** In the catalog, not on the platform. */
    LOCAL_ONLY
}
