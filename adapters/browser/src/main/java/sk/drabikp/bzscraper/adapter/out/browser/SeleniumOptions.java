package sk.drabikp.bzscraper.adapter.out.browser;

/**
 * A platform's browser switches, {@code bzscraper.<platform>.selenium.*}: whether the platform
 * is driven at all, headless (default) or visible, its own Chromium/chromedriver (blank: the
 * shared {@link BrowserProperties}) and profile directory (blank: one per account, see
 * {@link BrowserProfile#dir}).
 */
public record SeleniumOptions(boolean enabled, Boolean headless, String chromiumBinary, String chromedriver,
                              String profileDir) {

    /** Off, headless, everything else default. */
    public static final SeleniumOptions OFF = new SeleniumOptions(false, null, null, null, null);

    public SeleniumOptions {
        headless = headless == null || headless;
        chromiumBinary = chromiumBinary == null ? "" : chromiumBinary;
        chromedriver = chromedriver == null ? "" : chromedriver;
        profileDir = profileDir == null ? "" : profileDir;
    }

    /** The {@link PlatformBrowser} settings for {@code platform}, its profile the {@code account}'s own. */
    public PlatformBrowser.Settings browser(String platform, String account, BrowserProperties shared,
                                            String windowSize) {
        return new PlatformBrowser.Settings(platform, BrowserProfile.dir(profileDir, platform, account), headless,
                chromiumBinary.isBlank() ? shared.chromiumBinary() : chromiumBinary,
                chromedriver.isBlank() ? shared.chromedriver() : chromedriver, shared.passwordStore(), windowSize);
    }
}
