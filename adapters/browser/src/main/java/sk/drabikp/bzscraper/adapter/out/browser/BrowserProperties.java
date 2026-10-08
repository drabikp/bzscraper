package sk.drabikp.bzscraper.adapter.out.browser;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bzscraper.browser.*}: what every platform's browser shares — where saved logins are
 * encrypted ({@link BrowserProfile#secretStoreArgs}) and, unless a platform sets its own,
 * where Chromium and chromedriver are.
 */
@ConfigurationProperties("bzscraper.browser")
public record BrowserProperties(String passwordStore, String chromiumBinary, String chromedriver) {

    public BrowserProperties {
        passwordStore = passwordStore == null || passwordStore.isBlank() ? "auto" : passwordStore;
        chromiumBinary = chromiumBinary == null ? "" : chromiumBinary;
        chromedriver = chromedriver == null ? "" : chromedriver;
    }
}
