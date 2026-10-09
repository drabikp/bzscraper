package sk.drabikp.bzscraper.ui;

import com.vaadin.flow.component.notification.Notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** How the pages tell the user what happened, and how times read there. */
public final class Notices {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm:ss");

    private Notices() {
    }

    /** What the user's action did (shown for a while, out of the way). */
    public static void done(String text) {
        Notification.show(text, 6000, Notification.Position.BOTTOM_START);
    }

    /** A moment, e.g. "3 Oct 14:05:09" (the server's time zone); empty for none. */
    public static String when(Instant at) {
        return at == null ? "" : WHEN.format(at.atZone(ZoneId.systemDefault()));
    }
}
