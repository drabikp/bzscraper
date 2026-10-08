package sk.drabikp.bzscraper.application.port.out;

/** The calendar could not be read (not configured, unreachable, not a calendar). The message is shown to the user. */
public class CalendarUnavailableException extends Exception {

    public CalendarUnavailableException(String message) {
        super(message);
    }

    public CalendarUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
