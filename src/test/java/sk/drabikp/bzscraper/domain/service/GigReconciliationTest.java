package sk.drabikp.bzscraper.domain.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ReconciliationEntry;
import sk.drabikp.bzscraper.domain.model.ReconciliationResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigReconciliationTest {

    @Test
    void classifies_new_matched_conflict_and_local_only_by_identity() {
        // same date; identity = date + venue
        Gig matchedLocal = TestGigs.gig("A", "Klub 007");
        Gig matchedImported = TestGigs.gig("A", "Klub 007");        // identical -> MATCHED
        Gig conflictLocal = TestGigs.gig("Keep", "Barrák");
        Gig conflictImported = TestGigs.gig("Changed", "Barrák");   // same id, diff title -> CONFLICT
        Gig localOnly = TestGigs.gig("Solo", "Solo Venue");         // not on platform
        Gig imported = TestGigs.gig("Fresh", "New Venue");          // not in catalog -> NEW

        ReconciliationResult result = GigReconciliation.reconcile(
                List.of(matchedLocal, conflictLocal, localOnly),
                List.of(matchedImported, conflictImported, imported));

        assertThat(result.added()).extracting(e -> e.imported().title()).containsExactly("Fresh");
        assertThat(result.matched()).hasSize(1);
        assertThat(result.localOnly()).extracting(e -> e.local().title()).containsExactly("Solo");

        assertThat(result.conflicts()).hasSize(1);
        ReconciliationEntry conflict = result.conflicts().get(0);
        assertThat(conflict.local().title()).isEqualTo("Keep");
        assertThat(conflict.imported().title()).isEqualTo("Changed");
    }

    @Test
    void an_empty_import_marks_every_local_gig_local_only() {
        Gig a = TestGigs.gig("A", "Klub 007");

        ReconciliationResult result = GigReconciliation.reconcile(List.of(a), List.of());

        assertThat(result.localOnly()).hasSize(1);
        assertThat(result.added()).isEmpty();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    void reconciling_into_an_empty_catalog_marks_everything_new() {
        Gig a = TestGigs.gig("A", "Klub 007");

        ReconciliationResult result = GigReconciliation.reconcile(List.of(), List.of(a));

        assertThat(result.added()).hasSize(1);
        assertThat(result.localOnly()).isEmpty();
    }
}
