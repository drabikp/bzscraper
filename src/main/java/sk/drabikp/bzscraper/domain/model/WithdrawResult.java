package sk.drabikp.bzscraper.domain.model;

/** Result of withdrawing a gig from one platform. {@code detail} carries the error when failed. */
public record WithdrawResult(Platform platform, boolean succeeded, String detail) {

    public static WithdrawResult ok(Platform platform) {
        return new WithdrawResult(platform, true, null);
    }

    public static WithdrawResult failed(Platform platform, String detail) {
        return new WithdrawResult(platform, false, detail);
    }
}
