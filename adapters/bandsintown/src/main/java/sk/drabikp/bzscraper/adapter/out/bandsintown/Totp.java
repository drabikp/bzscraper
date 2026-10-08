package sk.drabikp.bzscraper.adapter.out.bandsintown;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Locale;

/**
 * Authenticator-app codes (RFC 6238 TOTP: HMAC-SHA1, 30-second steps, 6 digits) from
 * the base32 secret shown when two-factor authentication was set up.
 */
final class Totp {

    static final int STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final byte[] key;

    Totp(String base32Secret) {
        this.key = decodeBase32(base32Secret);
    }

    /** The code valid at {@code epochSeconds}. */
    String codeAt(long epochSeconds) {
        return code(epochSeconds / STEP_SECONDS, 6);
    }

    /** Seconds until the code valid at {@code epochSeconds} expires. */
    static long secondsLeft(long epochSeconds) {
        return STEP_SECONDS - epochSeconds % STEP_SECONDS;
    }

    String code(long counter, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            int code = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", code);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    static byte[] decodeBase32(String secret) {
        String clean = secret.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Authenticator secret is not valid base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
