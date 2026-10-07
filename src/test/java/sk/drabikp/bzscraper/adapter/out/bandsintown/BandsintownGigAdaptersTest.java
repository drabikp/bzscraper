package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.GigUpdateException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BandsintownGigAdaptersTest {

    private final BitPortalClient portalClient = mock(BitPortalClient.class);
    private final BitSession session = mock(BitSession.class);
    private final Gig gig = TestGigs.gig("A", "Klub 007");

    @Test
    void an_update_edits_the_event_by_id_and_closes_the_session() throws Exception {
        when(portalClient.openSession()).thenReturn(session);

        new BandsintownGigUpdater(portalClient).update("101", gig);

        verify(session).updateEvent("101", gig);
        verify(session).close();
    }

    @Test
    void a_cancelled_gig_is_not_updated_because_cancelling_removed_it() throws Exception {
        new BandsintownGigUpdater(portalClient).update("101", gig.cancel());

        verifyNoInteractions(portalClient);
    }

    @Test
    void an_update_failure_is_reported() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        doThrow(new BitUploadException("not recognised")).when(session).updateEvent(any(), any());

        assertThatThrownBy(() -> new BandsintownGigUpdater(portalClient).update("101", gig))
                .isInstanceOf(GigUpdateException.class).hasMessage("not recognised");
    }

    @Test
    void cancel_removes_the_event_with_the_cancelled_reason_and_delete_without_it() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        BandsintownGigWithdrawer withdrawer = new BandsintownGigWithdrawer(portalClient);

        withdrawer.withdraw("101", WithdrawAction.CANCEL);
        withdrawer.withdraw("102", WithdrawAction.DELETE);

        verify(session).deleteEvent("101", true);
        verify(session).deleteEvent("102", false);
    }

    @Test
    void a_failed_login_fails_the_withdrawal() throws Exception {
        when(portalClient.openSession()).thenThrow(new BitUploadException("login failed"));

        assertThatThrownBy(() -> new BandsintownGigWithdrawer(portalClient).withdraw("101", WithdrawAction.DELETE))
                .isInstanceOf(GigWithdrawalException.class).hasMessage("login failed");
    }

    @Test
    void a_delete_failure_is_reported() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        doThrow(new BitUploadException("row not found")).when(session).deleteEvent(any(), anyBoolean());

        assertThatThrownBy(() -> new BandsintownGigWithdrawer(portalClient).withdraw("101", WithdrawAction.CANCEL))
                .isInstanceOf(GigWithdrawalException.class).hasMessage("row not found");
    }
}
