package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.StepOutcome;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void the_form_edit_takes_past_events_one_at_a_time_and_passes_on_the_place_check() throws Exception {
        BandsintownFormEdit formEdit = new BandsintownFormEdit(portalClient);
        when(portalClient.openSession()).thenReturn(session);
        when(session.editEventInForm("101", gig)).thenReturn("Bandsintown placed it 40 km away");

        assertThat(formEdit.refusal(gig)).as("past events are what it is for").isEmpty();
        assertThat(formEdit.batchSize()).isEqualTo(1);
        assertThat(formEdit.run(List.of(new SyncStep.Item("101", gig)))).singleElement().satisfies(o -> {
            assertThat(o.kind()).isEqualTo(StepOutcome.Kind.DONE);
            assertThat(o.note()).contains("40 km");
        });
        verify(session).close();
    }

    @Test
    void the_form_edit_refuses_what_bandsintown_refused_and_retries_what_broke() throws Exception {
        BandsintownFormEdit formEdit = new BandsintownFormEdit(portalClient);
        when(portalClient.openSession()).thenReturn(session);
        when(session.editEventInForm(any(), any()))
                .thenThrow(new BitUploadException("no place in Hranice", null, true))
                .thenThrow(new BitUploadException("timeout"));

        assertThat(formEdit.run(List.of(new SyncStep.Item("101", gig), new SyncStep.Item("102", gig))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.REFUSED, StepOutcome.Kind.FAILED);
        assertThat(formEdit.run(List.of(new SyncStep.Item("103", gig.cancel()))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE);
    }

    @Test
    void cancel_removes_the_event_with_the_cancelled_reason_and_remove_without_it() throws Exception {
        when(portalClient.openSession()).thenReturn(session);

        assertThat(new BandsintownCancel(portalClient, clock).run(List.of(new SyncStep.Item("101", upcoming))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE);
        new BandsintownRemove(portalClient, clock).run(List.of(new SyncStep.Item("102", null)));

        verify(session).deleteEvent("101", true);
        verify(session).deleteEvent("102", false);
    }

    @Test
    void removal_doesnt_take_past_events_and_reports_failures() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        doThrow(new BitUploadException("row not found")).when(session).deleteEvent(any(), anyBoolean());
        BandsintownRemove remove = new BandsintownRemove(portalClient, clock);

        assertThat(remove.refusal(gig)).get().asString().contains("past events");
        assertThat(remove.refusal(upcoming)).isEmpty();
        assertThat(remove.run(List.of(new SyncStep.Item("101", upcoming))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.FAILED);
    }

    @Test
    void the_form_cancel_and_remove_take_past_events_through_the_events_own_form() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        BandsintownFormCancel formCancel = new BandsintownFormCancel(portalClient, clock);
        BandsintownFormRemove formRemove = new BandsintownFormRemove(portalClient, clock);

        assertThat(formCancel.refusal(gig)).as("2026-09-15 is past").isEmpty();
        assertThat(formRemove.refusal(gig)).isEmpty();
        assertThat(formCancel.run(List.of(new SyncStep.Item("101", gig))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE);
        assertThat(formRemove.run(List.of(new SyncStep.Item("102", null))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE);

        verify(session).deleteEventInForm("101", true);
        verify(session).deleteEventInForm("102", false);
        verify(session, never()).deleteEvent(any(), anyBoolean());
    }

    @Test
    void the_bulk_upload_records_published_events_and_tells_refused_rows_from_left_drafts() throws Exception {
        Gig b = TestGigs.gig("C", "Fléda", upcoming.schedule().start());
        Gig c = TestGigs.gig("D", "Kabinet", upcoming.schedule().start());
        when(portalClient.openSession()).thenReturn(session);
        when(session.createEvents(any(), anyBoolean())).thenReturn(List.of(
                BitSession.Created.published(upcoming, "301", "Bandsintown placed it 150 km from Hranice"),
                BitSession.Created.failed(b, "Bandsintown rejected this row — HTTP 200: …"),
                BitSession.Created.failed(c, "Uploaded to Bandsintown as event 303, but it is not published (DRAFT)")));

        List<StepOutcome> outcomes = new BandsintownBulkCreate(portalClient, false).run(List.of(
                new SyncStep.Item(null, upcoming), new SyncStep.Item(null, b), new SyncStep.Item(null, c)));

        assertThat(outcomes).extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE,
                StepOutcome.Kind.REFUSED, StepOutcome.Kind.FAILED_FOR_GOOD);
        assertThat(outcomes.getFirst().ref()).isEqualTo("301");
        assertThat(outcomes.getFirst().note()).contains("150 km");
        verify(session).createEvents(List.of(upcoming, b, c), false);
    }
}
