package sk.drabikp.bzscraper.domain.model;

/**
 * A kind of work a sync workflow is made of — one way of doing (part of) an action on a
 * platform. Platforms contribute implementations of the step types they support; the
 * engine runs a run's steps in the workflow's order and skips the ones its platform lacks.
 */
public enum StepType {

    /** Edit many events in one go (Bandsintown: one CSV upload with event ids). */
    BULK_EDIT(SyncAction.UPDATE, "bulk edit"),
    /** Edit one event at a time through the platform's edit form. */
    FORM_EDIT(SyncAction.UPDATE, "form edit");

    private final SyncAction action;
    private final String label;

    StepType(SyncAction action, String label) {
        this.action = action;
        this.label = label;
    }

    public SyncAction action() {
        return action;
    }

    /** How the step reads in the sync log ("bulk edit: refused …"). */
    public String label() {
        return label;
    }
}
