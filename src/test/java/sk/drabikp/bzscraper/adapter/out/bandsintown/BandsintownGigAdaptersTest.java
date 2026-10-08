package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
    private final BandsintownBulkEdit bulkEdit = new BandsintownBulkEdit(portalClient, clock);
    private final Gig upcoming = TestGigs.gig("B", "Barrák", ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0,
            ZoneId.of("Europe/Prague")));

    @Test
    void the_bulk_edit_uploads_every_event_at_once_and_judges_each_row() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        when(session.updateEvents(any())).thenReturn(List.of(
                new BitSession.Edited("101", null, null),
                new BitSession.Edited("102", "INVALID_START_TIME", null),
                new BitSession.Edited("103", null, "not applied — HTTP 200: …"),
                new BitSession.Edited("104", null, "Bandsintown made a new draft 900 instead of editing event 104")));

        List<StepOutcome> outcomes = bulkEdit.run(List.of(new SyncStep.Item("101", upcoming),
                new SyncStep.Item("102", upcoming), new SyncStep.Item("103", upcoming),
                new SyncStep.Item("104", upcoming)));

        assertThat(outcomes).extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE,
                StepOutcome.Kind.REFUSED, StepOutcome.Kind.FAILED, StepOutcome.Kind.FAILED_FOR_GOOD);
        assertThat(outcomes.get(1).note()).contains("INVALID_START_TIME");
        verify(session).close();
    }

    @Test
    void a_cancelled_gig_needs_no_upload_because_cancelling_removed_it() {
        assertThat(bulkEdit.run(List.of(new SyncStep.Item("101", upcoming.cancel()))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE);
        verifyNoInteractions(portalClient);
    }

    @Test
    void a_failed_upload_fails_every_row_to_try_again_and_past_events_are_not_taken() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        when(session.updateEvents(any())).thenThrow(new BitUploadException("timeout"));

        assertThat(bulkEdit.run(List.of(new SyncStep.Item("101", upcoming))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.FAILED);
        assertThat(bulkEdit.refusal(gig)).as("2026-09-15 is past").isPresent();
        assertThat(bulkEdit.refusal(upcoming)).isEmpty();
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
