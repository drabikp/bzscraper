package sk.drabikp.bzscraper.bandzone;

import org.springframework.boot.context.properties.ConfigurationProperties;
import sk.drabikp.bzscraper.browser.SeleniumOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code bzscraper.bandzone.*}. The band ({@code band-slug}) is read by the importer; the login
 * only when Bandzone is driven ({@code selenium.enabled}) — then the app doesn't start without
 * it. Secrets never leave this record ({@link #toString} hides them).
 */
@ConfigurationProperties("bzscraper.bandzone")
public record BandzoneProperties(String baseUrl, String login, String password, String bandSlug,
                                 SeleniumOptions selenium) {

    public BandzoneProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://bandzone.cz" : baseUrl;
        login = login == null ? "" : login;
        password = password == null ? "" : password;
        bandSlug = bandSlug == null ? "" : bandSlug;
        selenium = selenium == null ? SeleniumOptions.OFF : selenium;
    }

    /** Refuses to go on when Bandzone is switched on without its login and band. */
    public void requireLogin() {
        List<String> missing = new ArrayList<>();
        if (login.isBlank()) {
            missing.add("bzscraper.bandzone.login");
        }
        if (password.isBlank()) {
            missing.add("bzscraper.bandzone.password");
        }
        if (bandSlug.isBlank()) {
            missing.add("bzscraper.bandzone.band-slug");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Bandzone is switched on (bzscraper.bandzone.selenium.enabled) but "
                    + String.join(", ", missing) + " not set.");
        }
    }

    @Override
    public String toString() {
        return "BandzoneProperties[baseUrl=" + baseUrl + ", bandSlug=" + bandSlug + ", login=" + (login.isBlank()
                ? "" : "***") + ", password=" + (password.isBlank() ? "" : "***") + ", selenium=" + selenium + "]";
    }
}
