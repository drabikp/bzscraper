package sk.drabikp.bzscraper.domain.model;

/**
 * A kind of work a sync workflow is made of — one way of doing (part of) an action on a
 * platform. Platforms contribute implementations of the step types they support; the
 * engine runs a run's steps in the workflow's order and skips the ones its platform lacks.
 */
public enum StepType {

    /** Create many events in one go (Bandsintown: one CSV upload, then publish). */
    BULK_CREATE(SyncAction.PUBLISH, "bulk upload"),
    /** Create one event at a time through the platform's form (Bandzone: wizard + edit form). */
    FORM_CREATE(SyncAction.PUBLISH, "create form"),
    /** Edit many events in one go (Bandsintown: one CSV upload with event ids). */
    BULK_EDIT(SyncAction.UPDATE, "bulk edit"),
    /** Edit one event at a time through the platform's edit form. */
    FORM_EDIT(SyncAction.UPDATE, "form edit"),
    /** Mark the event cancelled (Bandsintown has no cancelled state: its cancel removes it). */
    CANCEL(SyncAction.CANCEL, "cancel"),
    /** Remove the event from the platform. */
    REMOVE(SyncAction.DELETE, "remove"),
    /**
     * Bring a cancelled event back: remove the cancelled copy and create it again (Bandzone
     * can't un-cancel). The engine builds it from the platform's remove and create steps.
     */
    RECREATE(SyncAction.REACTIVATE, "remove and create again");

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
