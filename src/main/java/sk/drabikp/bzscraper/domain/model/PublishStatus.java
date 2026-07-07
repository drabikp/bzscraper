package sk.drabikp.bzscraper.domain.model;

/**
 * Outcome of attempting to publish one gig to one platform.
 */
public enum PublishStatus {
    /** Newly published to the platform in this run. */
    PUBLISHED,
    /** Already published in a previous run (present in the uploaded-set); skipped. */
    SKIPPED_ALREADY_UPLOADED,
    /** Not publishable (e.g. missing start date required by the platform); skipped. */
    SKIPPED_INVALID,
    /** Publish was attempted but the platform/upload failed; safe to retry next run. */
    FAILED
}
