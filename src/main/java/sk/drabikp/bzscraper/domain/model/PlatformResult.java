package sk.drabikp.bzscraper.domain.model;

/**
 * Result of one operation on a published gig (withdraw, resync) on one platform.
 * {@code detail} carries the error when failed.
 */
public record PlatformResult(Platform platform, boolean succeeded, String detail) {

    public static PlatformResult ok(Platform platform) {
        return new PlatformResult(platform, true, null);
    }

    public static PlatformResult failed(Platform platform, String detail) {
        return new PlatformResult(platform, false, detail);
    }
}
