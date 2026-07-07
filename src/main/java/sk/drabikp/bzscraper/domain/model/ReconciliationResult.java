package sk.drabikp.bzscraper.domain.model;

import java.util.List;

/**
 * Outcome of reconciling an imported gig set against the local catalog: one
 * {@link ReconciliationEntry} per gig identity seen on either side.
 */
public record ReconciliationResult(List<ReconciliationEntry> entries) {

    public ReconciliationResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public List<ReconciliationEntry> withOutcome(ReconciliationOutcome outcome) {
        return entries.stream().filter(e -> e.outcome() == outcome).toList();
    }

    public List<ReconciliationEntry> added() {
        return withOutcome(ReconciliationOutcome.NEW);
    }

    public List<ReconciliationEntry> conflicts() {
        return withOutcome(ReconciliationOutcome.CONFLICT);
    }

    public List<ReconciliationEntry> matched() {
        return withOutcome(ReconciliationOutcome.MATCHED);
    }

    public List<ReconciliationEntry> localOnly() {
        return withOutcome(ReconciliationOutcome.LOCAL_ONLY);
    }
}
