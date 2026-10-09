package sk.drabikp.bzscraper.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind the proxy (online, HTTPS ends there): the request the app gets is plain HTTP, and the
 * proxy's X-Forwarded-Proto says how it really came in — then the sign-in's cookies are Secure.
 * A real server, as Tomcat itself reads the header (here from 127.0.0.1, a trusted address).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyHeadersTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void signed_in_over_https_at_the_proxy_the_cookies_are_secure() throws Exception {
        assertThat(signInCookies(true)).isNotEmpty()
                .filteredOn(c -> c.startsWith("JSESSIONID=") || c.startsWith("bzscraper-remember="))
                .hasSize(2)
                .allMatch(c -> c.contains("Secure"));
    }

    @Test
    void signed_in_over_plain_http_on_this_machine_they_are_not() throws Exception {
        assertThat(signInCookies(false))
                .filteredOn(c -> c.startsWith("JSESSIONID=") || c.startsWith("bzscraper-remember="))
                .hasSize(2)
                .noneMatch(c -> c.contains("Secure"));
    }

    private List<String> signInCookies(boolean https) throws Exception {
        HttpRequest.Builder csrf = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf"));
        if (https) {
            csrf.header("X-Forwarded-Proto", "https");
        }
        HttpResponse<String> first = http.send(csrf.build(), HttpResponse.BodyHandlers.ofString());
        String token = first.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                .findFirst().orElseThrow();

        HttpRequest.Builder login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"tester\",\"password\":\"test-only\",\"remember\":true}"));
        if (https) {
            login.header("X-Forwarded-Proto", "https");
        }
        HttpResponse<String> signedIn = http.send(login.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(signedIn.statusCode()).isEqualTo(200);
        return signedIn.headers().allValues("Set-Cookie");
    }
}
