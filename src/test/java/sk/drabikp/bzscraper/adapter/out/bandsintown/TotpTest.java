package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpTest {

    /** RFC 6238 appendix B: the ASCII secret "12345678901234567890", base32-encoded. */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matches_the_rfc_6238_sha1_test_vectors() {
        Totp totp = new Totp(RFC_SECRET);

        assertThat(totp.code(59 / 30, 8)).isEqualTo("94287082");
        assertThat(totp.code(1111111109L / 30, 8)).isEqualTo("07081804");
        assertThat(totp.code(1234567890L / 30, 8)).isEqualTo("89005924");
        assertThat(totp.code(2000000000L / 30, 8)).isEqualTo("69279037");
    }

    @Test
    void authenticator_codes_are_the_last_six_digits() {
        assertThat(new Totp(RFC_SECRET).codeAt(59)).isEqualTo("287082");
    }

    @Test
    void the_secret_may_be_lowercase_and_grouped_with_spaces() {
        assertThat(new Totp("gezd gnbv gy3t qojq gezd gnbv gy3t qojq").codeAt(59)).isEqualTo("287082");
    }

    @Test
    void reports_seconds_left_in_the_current_window() {
        assertThat(Totp.secondsLeft(60)).isEqualTo(30);
        assertThat(Totp.secondsLeft(89)).isEqualTo(1);
    }

    @Test
    void rejects_a_secret_that_is_not_base32() {
        assertThatThrownBy(() -> new Totp("not base32!")).isInstanceOf(IllegalArgumentException.class);
    }
}
