package sk.drabikp.bzscraper.bandsintown;

import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;

/** A Bandsintown operation failed; {@link #kind()} says whether to try again, another way, or ask the user. */
public class BitUploadException extends PlatformException {

    /** A temporary failure. */
    public BitUploadException(String message) {
        this(message, null, FailureKind.TEMPORARY);
    }

    /** A temporary failure. */
    public BitUploadException(String message, Throwable cause) {
        this(message, cause, FailureKind.TEMPORARY);
    }

    public BitUploadException(String message, Throwable cause, FailureKind kind) {
        super(message, cause, kind);
    }

    /** Bandsintown said no to doing it this way; nothing happened there. */
    public static BitUploadException refused(String message) {
        return new BitUploadException(message, null, FailureKind.REFUSED);
    }

    /** Only the user can sort it out (switched off, data to correct). */
    public static BitUploadException needsUser(String message) {
        return new BitUploadException(message, null, FailureKind.NEEDS_USER);
    }

    /** The browser is busy with another operation — not tried; again later. */
    public static BitUploadException busy(String message) {
        return new BitUploadException(message, null, FailureKind.BUSY);
    }
}
