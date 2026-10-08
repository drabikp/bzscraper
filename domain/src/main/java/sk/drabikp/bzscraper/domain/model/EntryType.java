package sk.drabikp.bzscraper.domain.model;

/**
 * How admission to a gig is priced: a fee (manual amount), voluntary, or free. Each
 * platform maps it to its own entry options.
 */
public enum EntryType {
    /** A fixed ticket/entry price (see {@code Gig.entryFee}). */
    PAID,
    /** Voluntary contribution at the door. */
    VOLUNTARY,
    /** Free entry. */
    FREE
}
