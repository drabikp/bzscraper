package sk.drabikp.bzscraper.bandsintown;

import org.springframework.boot.context.properties.ConfigurationProperties;
import sk.drabikp.bzscraper.browser.SeleniumOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code bzscraper.bandsintown.*}. The artist ({@code artist-name}, as Bandsintown spells it —
 * it goes into every uploaded row) is needed for the CSV download too; the login only when
 * Bandsintown is driven ({@code selenium.enabled}) — then the app doesn't start without it.
 * {@code notify-followers} is off unless set. Secrets never leave this record ({@link #toString}
 * hides them).
 */
@ConfigurationProperties("bzscraper.bandsintown")
public record BandsintownProperties(String baseUrl, String login, String password, String totpSecret,
                                    String artistId, String artistName, boolean notifyFollowers,
                                    SeleniumOptions selenium, Pacing pacing) {

    /** {@code pacing.min-ms} / {@code max-ms}: the human pauses between steps (see {@link HumanPacer}). */
    public record Pacing(Long minMs, Long maxMs) {

        public Pacing {
            minMs = minMs == null ? 900 : minMs;
            maxMs = maxMs == null ? 2600 : maxMs;
        }
    }

    public BandsintownProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://artists.bandsintown.com" : baseUrl;
        login = login == null ? "" : login;
        password = password == null ? "" : password;
        totpSecret = totpSecret == null ? "" : totpSecret;
        artistId = artistId == null ? "" : artistId;
        artistName = artistName == null ? "" : artistName;
        selenium = selenium == null ? SeleniumOptions.OFF : selenium;
        pacing = pacing == null ? new Pacing(null, null) : pacing;
    }

    /** Refuses to go on when Bandsintown is switched on without its login and artist. */
    public void requireLogin() {
        List<String> missing = new ArrayList<>();
        if (login.isBlank()) {
            missing.add("bzscraper.bandsintown.login");
        }
        if (password.isBlank()) {
            missing.add("bzscraper.bandsintown.password");
        }
        if (artistName.isBlank()) {
            missing.add("bzscraper.bandsintown.artist-name");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Bandsintown is switched on (bzscraper.bandsintown.selenium.enabled) but "
                    + String.join(", ", missing) + " not set.");
        }
    }

    @Override
    public String toString() {
        return "BandsintownProperties[baseUrl=" + baseUrl + ", artistId=" + artistId + ", artistName=" + artistName
                + ", notifyFollowers=" + notifyFollowers + ", login=" + (login.isBlank() ? "" : "***")
                + ", password=" + (password.isBlank() ? "" : "***") + ", totpSecret="
                + (totpSecret.isBlank() ? "" : "***") + ", selenium=" + selenium + ", pacing=" + pacing + "]";
    }
}
