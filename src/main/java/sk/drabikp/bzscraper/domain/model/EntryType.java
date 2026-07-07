package sk.drabikp.bzscraper.domain.model;

/**
 * How admission to a gig is priced. Maps to Bandzone's entry-type radio
 * (manual amount / voluntary / free).
 */
public enum EntryType {
    /** A fixed ticket/entry price (see {@code Gig.entryFee}). */
    PAID,
    /** Voluntary contribution at the door. */
    VOLUNTARY,
    /** Free entry. */
    FREE
}
