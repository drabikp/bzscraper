package sk.drabikp.bzscraper.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bzscraper.auth.*}: the one account that may use the app. {@code password} as is, or
 * encoded with its id ({@code {bcrypt}$2a$…}); empty → a random one, written to the log on start
 * (fine on a developer's machine, never on a server). {@code remember-me-key} signs the
 * "remember me" cookie; empty → derived from the account, so changing the password signs
 * everyone out. Secrets never leave this record ({@link #toString}).
 */
@ConfigurationProperties("bzscraper.auth")
public record AuthProperties(String username, String password, String rememberMeKey, Integer rememberMeDays) {

    public AuthProperties {
        username = username == null || username.isBlank() ? "admin" : username.strip();
        password = password == null ? "" : password;
        rememberMeKey = rememberMeKey == null ? "" : rememberMeKey;
        rememberMeDays = rememberMeDays == null ? 30 : rememberMeDays;
    }

    @Override
    public String toString() {
        return "AuthProperties[username=" + username + ", password=" + (password.isEmpty() ? "" : "***")
                + ", rememberMeKey=" + (rememberMeKey.isEmpty() ? "" : "***") + ", rememberMeDays=" + rememberMeDays
                + "]";
    }
}
