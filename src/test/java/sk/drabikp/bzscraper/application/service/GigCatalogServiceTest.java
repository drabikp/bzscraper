package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GigCatalogServiceTest {

    private final GigRepository repository = mock(GigRepository.class);
    private final PublishedGigStore publishedGigStore = mock(PublishedGigStore.class);
    private final Transactions transactions = mock(Transactions.class);
    private final GigCatalogService service = new GigCatalogService(repository, publishedGigStore, transactions);

    GigCatalogServiceTest() {
        doAnswer(inv -> {
            inv.<Runnable>getArgument(0).run();
            return null;
        }).when(transactions).inTransaction(any());
    }

    @Test
    void save_and_delete_delegate_to_the_repository() {
        Gig gig = TestGigs.gig("A", "Klub 007");

        service.save(gig);
        service.delete(gig.id());

        verify(repository).save(gig);
        verify(repository).deleteById(gig.id());
    }

    @Test
    void editing_without_moving_identity_just_upserts() {
        Gig original = TestGigs.gig("A", "Klub 007");
        Gig edited = TestGigs.gig("Renamed", "Klub 007"); // same date + venue -> same id

        service.update(original.id(), edited);

        verify(repository, never()).deleteById(any());
        verify(publishedGigStore, never()).move(any(), any());
        verify(repository).save(edited);
    }

    @Test
    void editing_that_moves_identity_replaces_the_row_and_moves_the_published_records_in_one_transaction() {
        Gig original = TestGigs.gig("A", "Klub 007");
        Gig movedVenue = TestGigs.gig("A", "Barrák"); // different venue -> different id

        service.update(original.id(), movedVenue);

        var order = inOrder(transactions, repository, publishedGigStore);
        order.verify(transactions).inTransaction(any());
        order.verify(repository).deleteById(original.id());
        order.verify(publishedGigStore).move(original.id(), movedVenue.id());
        order.verify(repository).save(movedVenue);
    }

    @Test
    void cancel_marks_the_stored_gig_cancelled() {
        Gig gig = TestGigs.gig("A", "Klub 007");
        when(repository.findById(gig.id())).thenReturn(Optional.of(gig));

        service.cancel(gig.id());

        verify(repository).save(argThat(Gig::cancelled));
    }

    @Test
    void reactivate_clears_the_cancelled_flag() {
        Gig cancelled = TestGigs.gig("A", "Klub 007").cancel();
        when(repository.findById(cancelled.id())).thenReturn(Optional.of(cancelled));

        service.reactivate(cancelled.id());

        verify(repository).save(argThat(g -> !g.cancelled()));
    }

    @Test
    void cancel_of_an_unknown_gig_is_a_no_op() {
        Gig gig = TestGigs.gig("Ghost", "Nowhere");
        when(repository.findById(gig.id())).thenReturn(Optional.empty());

        service.cancel(gig.id());

        verify(repository, never()).save(any());
    }

    @Test
    void all_gigs_reads_from_the_repository() {
        Gig gig = TestGigs.gig("A", "Klub 007");
        when(repository.findAll()).thenReturn(List.of(gig));

        assertThat(service.allGigs()).containsExactly(gig);
    }
}
