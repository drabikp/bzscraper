package sk.drabikp.bzscraper.bandzone;

import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;

/** A Bandzone operation failed; {@link #kind()} says whether to try again, another way, or ask the user. */
public class BandzoneUploadException extends PlatformException {

    /** A temporary failure. */
    public BandzoneUploadException(String message) {
        this(message, null, FailureKind.TEMPORARY);
    }

    /** A temporary failure. */
    public BandzoneUploadException(String message, Throwable cause) {
        this(message, cause, FailureKind.TEMPORARY);
    }

    public BandzoneUploadException(String message, Throwable cause, FailureKind kind) {
        super(message, cause, kind);
    }

    /** Bandzone said no to doing it this way; nothing happened there. */
    public static BandzoneUploadException refused(String message) {
        return new BandzoneUploadException(message, null, FailureKind.REFUSED);
    }

    /** Only the user can sort it out (switched off, data to correct). */
    public static BandzoneUploadException needsUser(String message) {
        return new BandzoneUploadException(message, null, FailureKind.NEEDS_USER);
    }

    /** The browser is busy with another operation — not tried; again later. */
    public static BandzoneUploadException busy(String message) {
        return new BandzoneUploadException(message, null, FailureKind.BUSY);
    }
}
