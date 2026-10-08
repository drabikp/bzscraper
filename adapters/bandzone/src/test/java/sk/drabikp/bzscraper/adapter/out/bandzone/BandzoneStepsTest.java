package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.StepOutcome;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BandzoneStepsTest {

    private final BandzonePortalClient portalClient = mock(BandzonePortalClient.class);
    private final BandzoneSession session = mock(BandzoneSession.class);
    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    @Test
    void create_makes_the_concert_and_completes_it_through_the_edit_form() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        when(session.createGig(gig)).thenReturn("563400");

        List<StepOutcome> outcomes = new BandzoneFormCreate(portalClient).run(List.of(new SyncStep.Item(null, gig)));

        assertThat(outcomes).singleElement().satisfies(o -> {
            assertThat(o.kind()).isEqualTo(StepOutcome.Kind.DONE);
            assertThat(o.ref()).isEqualTo("563400");
        });
        verify(session).updateGig("563400", gig);
        verify(session).close();
    }

    @Test
    void a_created_concert_counts_as_created_even_when_completing_it_fails() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        when(session.createGig(gig)).thenReturn("563400");
        doThrow(new BandzoneUploadException("poster upload timed out")).when(session).updateGig(any(), any());

        StepOutcome outcome = new BandzoneFormCreate(portalClient).run(List.of(new SyncStep.Item(null, gig))).getFirst();

        assertThat(outcome.ref()).isEqualTo("563400");
        assertThat(outcome.note()).contains("not all details were saved", "Re-sync");
    }

    @Test
    void a_town_bandzone_doesnt_know_needs_the_user_and_a_timeout_is_tried_again() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        when(session.createGig(gig))
                .thenThrow(BandzoneUploadException.needsUser("Bandzone doesn't know the town X — correct the city"))
                .thenThrow(new BandzoneUploadException("timeout"));
        BandzoneFormCreate create = new BandzoneFormCreate(portalClient);

        assertThat(create.run(List.of(new SyncStep.Item(null, gig))).getFirst().kind())
                .isEqualTo(StepOutcome.Kind.FAILED_FOR_GOOD);
        assertThat(create.run(List.of(new SyncStep.Item(null, gig))).getFirst().kind()).isEqualTo(StepOutcome.Kind.FAILED);
    }

    @Test
    void cancel_and_remove_drive_the_delete_tab() throws Exception {
        when(portalClient.openSession()).thenReturn(session);

        new BandzoneCancel(portalClient).run(List.of(new SyncStep.Item("100", gig)));
        new BandzoneRemove(portalClient).run(List.of(new SyncStep.Item("101", null)));

        verify(session).cancelGig("100");
        verify(session).deleteGig("101");
    }
}
