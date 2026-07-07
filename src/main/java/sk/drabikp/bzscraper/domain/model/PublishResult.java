package sk.drabikp.bzscraper.domain.model;

/**
 * Per-gig result of a publish run, tagged with the target {@link Platform} so a
 * multi-platform publish yields one flat, attributable result list. {@code detail}
 * carries a human-readable reason for SKIPPED_INVALID / FAILED (null otherwise).
 */
public record PublishResult(Platform platform, Gig gig, PublishStatus status, String detail) {

    public static PublishResult published(Platform platform, Gig gig) {
        return new PublishResult(platform, gig, PublishStatus.PUBLISHED, null);
    }

    public static PublishResult alreadyUploaded(Platform platform, Gig gig) {
        return new PublishResult(platform, gig, PublishStatus.SKIPPED_ALREADY_UPLOADED, null);
    }

    public static PublishResult skippedInvalid(Platform platform, Gig gig, String reason) {
        return new PublishResult(platform, gig, PublishStatus.SKIPPED_INVALID, reason);
    }

    public static PublishResult failed(Platform platform, Gig gig, String reason) {
        return new PublishResult(platform, gig, PublishStatus.FAILED, reason);
    }
}
