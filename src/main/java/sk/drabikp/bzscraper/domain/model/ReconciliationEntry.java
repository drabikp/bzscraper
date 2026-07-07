package sk.drabikp.bzscraper.domain.model;

/**
 * One gig identity's reconciliation between the local catalog and an imported set.
 * Exactly which of {@code local}/{@code imported} are present depends on the outcome:
 * NEW → imported only; LOCAL_ONLY → local only; MATCHED/CONFLICT → both.
 */
public record ReconciliationEntry(GigId id, ReconciliationOutcome outcome, Gig local, Gig imported) {

    public static ReconciliationEntry added(Gig imported) {
        return new ReconciliationEntry(imported.id(), ReconciliationOutcome.NEW, null, imported);
    }

    public static ReconciliationEntry matched(Gig local, Gig imported) {
        return new ReconciliationEntry(local.id(), ReconciliationOutcome.MATCHED, local, imported);
    }

    public static ReconciliationEntry conflict(Gig local, Gig imported) {
        return new ReconciliationEntry(local.id(), ReconciliationOutcome.CONFLICT, local, imported);
    }

    public static ReconciliationEntry localOnly(Gig local) {
        return new ReconciliationEntry(local.id(), ReconciliationOutcome.LOCAL_ONLY, local, null);
    }
}
