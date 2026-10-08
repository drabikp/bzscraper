package sk.drabikp.bzscraper.domain.service;

import sk.drabikp.bzscraper.domain.model.StepType;
import sk.drabikp.bzscraper.domain.model.SyncAction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The sync workflows: for each action, the ways of doing it in the order they are tried —
 * cheapest and sturdiest first (one bulk upload for many gigs before a form per gig). What
 * no step can do is left to the user.
 */
public final class Workflows {

    private static final Map<SyncAction, List<StepType>> STEPS = Map.of(
            SyncAction.PUBLISH, List.of(StepType.BULK_CREATE, StepType.FORM_CREATE),
            SyncAction.UPDATE, List.of(StepType.BULK_EDIT, StepType.FORM_EDIT),
            SyncAction.CANCEL, List.of(StepType.CANCEL, StepType.FORM_CANCEL),
            SyncAction.DELETE, List.of(StepType.REMOVE, StepType.FORM_REMOVE),
            SyncAction.REACTIVATE, List.of(StepType.RECREATE));

    private Workflows() {
    }

    public static Optional<List<StepType>> of(SyncAction action) {
        return Optional.ofNullable(STEPS.get(action));
    }

    /** The step after {@code step} in its workflow, if any. */
    public static Optional<StepType> after(StepType step) {
        List<StepType> steps = STEPS.get(step.action());
        int next = steps.indexOf(step) + 1;
        return next < steps.size() ? Optional.of(steps.get(next)) : Optional.empty();
    }
}
