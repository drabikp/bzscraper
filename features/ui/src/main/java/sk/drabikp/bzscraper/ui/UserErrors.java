package sk.drabikp.bzscraper.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.gig.application.UserFacingException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * How the pages run a user's action: when a rule of the catalog or sync says no
 * ({@link UserFacingException}), its message is shown as it is; anything else is a fault —
 * logged, and the user told briefly, never a raw exception text.
 */
public final class UserErrors {

    private static final Logger log = LoggerFactory.getLogger(UserErrors.class);

    private UserErrors() {
    }

    /** Runs the action; false when it was refused or failed (the user was told). */
    public static boolean attempt(Runnable action) {
        return attempt(() -> {
            action.run();
            return Boolean.TRUE;
        }).isPresent();
    }

    /** The action's result, or empty when it was refused or failed (the user was told). */
    public static <T> Optional<T> attempt(Supplier<T> action) {
        try {
            return Optional.ofNullable(action.get());
        } catch (UserFacingException refused) {
            show(refused.getMessage());
        } catch (RuntimeException fault) {
            log.error("A user action failed", fault);
            show("Something went wrong — nothing may have changed. Details are in the log.");
        }
        return Optional.empty();
    }

    /**
     * What to tell the user about a failure in background work (shown later, on the page's
     * thread): a refusal or a platform's / the calendar's own reason as it is; anything else is
     * logged and summed up.
     */
    public static String describe(Exception failure) {
        if (failure instanceof RuntimeException fault && !(failure instanceof UserFacingException)) {
            log.error("Background work failed", fault);
            return "something went wrong — details are in the log";
        }
        return failure.getMessage();
    }

    private static void show(String message) {
        Notification.show(message, 6000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
