package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ImportDecision;
import sk.drabikp.bzscraper.domain.model.ImportPlan;
import sk.drabikp.bzscraper.domain.model.ImportProposal;
import sk.drabikp.bzscraper.domain.model.ImportResult;
import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GigImportServiceTest {

    private final GigRepository repository = mock(GigRepository.class);
    private final PublishedGigStore store = mock(PublishedGigStore.class);
    private final Transactions transactions = mock(Transactions.class);
    private final GigImporter bandzone = mock(GigImporter.class);
    private final GigImporter bandsintown = mock(GigImporter.class);

    private final Gig klub = TestGigs.gig("Fest", "Klub 007");
    private final Gig klubRenamed = TestGigs.gig("Fest 2026", "Klub 007");

    GigImportServiceTest() {
        when(bandzone.platform()).thenReturn(Platform.BANDZONE);
        when(bandsintown.platform()).thenReturn(Platform.BANDSINTOWN);
        doAnswer(inv -> {
            inv.<Runnable>getArgument(0).run();
            return null;
        }).when(transactions).inTransaction(any());
    }

    private GigImportService service() {
        return new GigImportService(List.of(bandzone, bandsintown), repository, store, transactions);
    }

    @Test
    void importable_platforms_are_the_registered_importers() {
        assertThat(service().importablePlatforms()).containsExactly(Platform.BANDZONE, Platform.BANDSINTOWN);
    }

    @Test
    void a_platform_that_cannot_be_read_is_reported_and_the_others_are_still_planned() {
        when(bandzone.importGigs()).thenReturn(List.of(new ImportedGig(Platform.BANDZONE, klub, "100")));
        when(bandsintown.importGigs()).thenThrow(new IllegalStateException("authenticator code rejected"));

        ImportPlan plan = service().plan(Set.of(Platform.BANDZONE, Platform.BANDSINTOWN));

        assertThat(plan.proposals()).hasSize(1);
        assertThat(plan.failures()).containsEntry(Platform.BANDSINTOWN, "authenticator code rejected");
    }

    @Test
    void a_new_gig_found_on_both_platforms_is_saved_once_and_linked_to_both() {
        ImportProposal proposal = new ImportProposal(null, List.of(
                new ImportedGig(Platform.BANDZONE, klub, "100"),
                new ImportedGig(Platform.BANDSINTOWN, klub, "900")), false);

        ImportResult result = service().apply(List.of(ImportDecision.byDefault(proposal)));

        verify(repository).save(klub);
        verify(store).record(Platform.BANDZONE, klub.id(), "100");
        verify(store).record(Platform.BANDSINTOWN, klub.id(), "900");
        assertThat(result).isEqualTo(new ImportResult(1, 0, 2, 0));
    }

    @Test
    void linking_to_a_catalog_gig_with_the_platform_version_chosen_updates_it() {
        ImportProposal proposal = new ImportProposal(klub,
                List.of(new ImportedGig(Platform.BANDZONE, klubRenamed, "100")), false);
        ImportProposal.Version platformVersion = proposal.versions().get(1);

        ImportResult result = service().apply(List.of(new ImportDecision(proposal, true, true, platformVersion)));

        verify(repository).save(klubRenamed);
        verify(store).record(Platform.BANDZONE, klubRenamed.id(), "100");
        assertThat(result).isEqualTo(new ImportResult(0, 1, 1, 0));
    }

    @Test
    void keeping_the_catalog_version_only_links_the_platform_event() {
        ImportProposal proposal = new ImportProposal(klub,
                List.of(new ImportedGig(Platform.BANDZONE, klubRenamed, "100")), false);

        ImportResult result = service().apply(List.of(ImportDecision.byDefault(proposal)));

        verify(repository, never()).save(any());
        verify(store).record(Platform.BANDZONE, klub.id(), "100");
        assertThat(result).isEqualTo(new ImportResult(0, 0, 1, 0));
    }

    @Test
    void choosing_a_version_with_another_venue_moves_the_catalog_gig_and_its_links() {
        Gig elsewhere = TestGigs.gig("Fest", "Lucerna");
        ImportProposal proposal = new ImportProposal(klub,
                List.of(new ImportedGig(Platform.BANDSINTOWN, elsewhere, "900")), true);

        service().apply(List.of(new ImportDecision(proposal, true, true, proposal.versions().get(1))));

        verify(repository).deleteById(klub.id());
        verify(store).move(klub.id(), elsewhere.id());
        verify(repository).save(any());
        verify(store).record(Platform.BANDSINTOWN, elsewhere.id(), "900");
    }

    @Test
    void a_rejected_suggestion_imports_each_copy_on_its_own() {
        Gig bz = TestGigs.gig("Fest", "Lucerna Music Bar");
        Gig bit = TestGigs.gig("Fest", "Lucerna");
        ImportProposal proposal = new ImportProposal(null, List.of(
                new ImportedGig(Platform.BANDZONE, bz, "100"),
                new ImportedGig(Platform.BANDSINTOWN, bit, "900")), true);
        when(repository.findById(any())).thenReturn(Optional.empty());

        ImportResult result = service().apply(List.of(ImportDecision.byDefault(proposal)));   // suggestion: not confirmed

        verify(repository).save(bz);
        verify(repository).save(bit);
        verify(store).record(Platform.BANDZONE, bz.id(), "100");
        verify(store).record(Platform.BANDSINTOWN, bit.id(), "900");
        assertThat(result.added()).isEqualTo(2);
    }

    @Test
    void unticked_proposals_are_not_imported() {
        ImportProposal proposal = new ImportProposal(null, List.of(new ImportedGig(Platform.BANDZONE, klub, "100")), false);

        service().apply(List.of(new ImportDecision(proposal, false, true, proposal.defaultVersion())));

        verify(repository, never()).save(any());
        verify(store, never()).record(any(), any(), any());
    }

    @Test
    void two_importers_for_the_same_platform_is_rejected() {
        assertThatThrownBy(() -> new GigImportService(List.of(bandzone, bandzone), repository, store, transactions))
                .isInstanceOf(IllegalStateException.class);
    }
}
