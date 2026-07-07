package sk.drabikp.bzscraper.domain.model;

/**
 * Per-gig result of a publish run, tagged with the target {@link Platform}.
 * {@code detail} carries a reason for SKIPPED_INVALID / FAILED; {@code externalRef}
 * carries the platform's id for a PUBLISHED gig when the platform returns one (null
 * otherwise) so the gig can later be updated/cancelled/deleted there.
 */
public record PublishResult(Platform platform, Gig gig, PublishStatus status, String detail, String externalRef) {

    public static PublishResult published(Platform platform, Gig gig) {
        return published(platform, gig, null);
    }

    public static PublishResult published(Platform platform, Gig gig, String externalRef) {
        return new PublishResult(platform, gig, PublishStatus.PUBLISHED, null, externalRef);
    }

    public static PublishResult alreadyUploaded(Platform platform, Gig gig) {
        return new PublishResult(platform, gig, PublishStatus.SKIPPED_ALREADY_UPLOADED, null, null);
    }

    public static PublishResult skippedInvalid(Platform platform, Gig gig, String reason) {
        return new PublishResult(platform, gig, PublishStatus.SKIPPED_INVALID, reason, null);
    }

    public static PublishResult failed(Platform platform, Gig gig, String reason) {
        return new PublishResult(platform, gig, PublishStatus.FAILED, reason, null);
    }
}
