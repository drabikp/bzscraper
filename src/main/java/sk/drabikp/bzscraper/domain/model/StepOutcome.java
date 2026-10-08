package sk.drabikp.bzscraper.domain.model;

/**
 * What one step did with one gig.
 * <ul>
 *   <li>{@link Kind#DONE} — finished; {@code note} is optional.</li>
 *   <li>{@link Kind#REFUSED} — the platform said a definite "no" to this gig
 *       ({@code INVALID_START_TIME}): another step may do it, if the action is safe to repeat.</li>
 *   <li>{@link Kind#FAILED} — technical (timeout, login lost): try this step again later;
 *       never move on — it may have half-happened.</li>
 *   <li>{@link Kind#FAILED_FOR_GOOD} — trying again won't help and nothing else should try
 *       (e.g. the platform made a new event instead of editing): the user decides.</li>
 * </ul>
 */
public record StepOutcome(Kind kind, String note) {

    public enum Kind { DONE, REFUSED, FAILED, FAILED_FOR_GOOD }

    public StepOutcome {
        if (kind == null) {
            throw new IllegalArgumentException("outcome kind is required");
        }
        if (kind != Kind.DONE && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("a refusal or failure needs a reason");
        }
    }

    public static StepOutcome done(String note) {
        return new StepOutcome(Kind.DONE, note);
    }

    public static StepOutcome refused(String reason) {
        return new StepOutcome(Kind.REFUSED, reason);
    }

    public static StepOutcome failed(String error) {
        return new StepOutcome(Kind.FAILED, error);
    }

    public static StepOutcome failedForGood(String error) {
        return new StepOutcome(Kind.FAILED_FOR_GOOD, error);
    }
}
