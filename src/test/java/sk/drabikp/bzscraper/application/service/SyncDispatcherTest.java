package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.GigUpdateException;
import sk.drabikp.bzscraper.application.port.out.GigUpdater;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDSINTOWN;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDZONE;

class SyncDispatcherTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();

    private final GigPublisher bzPublisher = mock(GigPublisher.class);
    private final GigUpdater bzUpdater = mock(GigUpdater.class);
    private final GigWithdrawer bzWithdrawer = mock(GigWithdrawer.class);
    private final GigWithdrawer bitWithdrawer = mock(GigWithdrawer.class);

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");
    private final Gig other = TestGigs.gig("Other", "Barrák",
            ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

    SyncDispatcherTest() {
        when(bzPublisher.platform()).thenReturn(BANDZONE);
        when(bzUpdater.platform()).thenReturn(BANDZONE);
        when(bzWithdrawer.platform()).thenReturn(BANDZONE);
        when(bitWithdrawer.platform()).thenReturn(BANDSINTOWN);
        // these gigs are in the past (clock: 2026-10-08); past-event rules are tested in SyncRequestsTest
        when(bzPublisher.publishesPastEvents()).thenReturn(true);
        when(bzUpdater.updatesPastEvents()).thenReturn(true);
        when(bzWithdrawer.withdrawsPastEvents(any())).thenReturn(true);
        when(bitWithdrawer.withdrawsPastEvents(any())).thenReturn(true);
    }

    private SyncDispatcher dispatcher() {
        return new SyncDispatcher(List.of(bzPublisher), List.of(bzUpdater), List.of(bzWithdrawer, bitWithdrawer),
                gigs, published, outbox, new SyncFakes.DirectTransactions(), signals, clock);
    }

    private SyncTask queue(Gig g, Platform platform, SyncAction action) {
        return outbox.enqueue(g.id(), SyncTask.labelOf(g), platform, action, clock.instant());
    }

    private SyncTask after(SyncTask task) {
        return outbox.get(task.id());
    }

    // --- update ---

    @Test
    void an_update_pushes_the_gigs_details_as_they_are_when_it_runs() throws Exception {
        gigs.save(gig);
        published.record(BANDZONE, gig.id(), "100");
        SyncTask task = queue(gig, BANDZONE, SyncAction.UPDATE);
        Gig renamedSince = new Gig("Fest 2026", gig.schedule(), gig.location(), gig.lineup(), gig.admission(),
                null, null, null, null, false);
        gigs.save(renamedSince);

        assertThat(dispatcher().runNext()).isTrue();

        verify(bzUpdater).update("100", renamedSince);
        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
        assertThat(after(task).attempts()).isEqualTo(1);
        assertThat(signals.changes).isPositive();
        assertThat(dispatcher().runNext()).isFalse();
    }

    @Test
    void a_failing_update_is_retried_after_1_5_and_15_minutes_then_left_for_the_user() throws Exception {
        gigs.save(gig);
        published.record(BANDZONE, gig.id(), "100");
        doThrow(new GigUpdateException("timeout")).when(bzUpdater).update(any(), any());
        SyncTask task = queue(gig, BANDZONE, SyncAction.UPDATE);
        SyncDispatcher dispatcher = dispatcher();

        dispatcher.runNext();
        assertThat(after(task).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(after(task).nextAttemptAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
        assertThat(dispatcher.runNext()).as("not due yet").isFalse();

        for (int minutes : new int[]{1, 5, 15}) {
            clock.advance(Duration.ofMinutes(minutes));
            assertThat(dispatcher.runNext()).isTrue();
        }

        assertThat(after(task).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(task).attempts()).isEqualTo(4);
        assertThat(after(task).message()).isEqualTo("timeout");
        verify(bzUpdater, times(4)).update("100", gig);
    }

    @Test
    void a_platform_that_cannot_update_fails_at_once_without_retrying() {
        gigs.save(gig);
        published.record(BANDSINTOWN, gig.id(), "900");
        SyncTask task = queue(gig, BANDSINTOWN, SyncAction.UPDATE);

        dispatcher().runNext();

        assertThat(after(task).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(task).message()).contains("update it there by hand");
    }

    @Test
    void work_that_became_moot_ends_done_with_a_note() throws Exception {
        gigs.save(gig);
        SyncTask update = queue(gig, BANDZONE, SyncAction.UPDATE);
        SyncTask cancel = queue(gig, BANDZONE, SyncAction.CANCEL);

        dispatcher().runNext();
        dispatcher().runNext();

        assertThat(after(update).status()).isEqualTo(SyncStatus.DONE);
        assertThat(after(update).message()).isEqualTo("not published there — nothing to update");
        assertThat(after(cancel).status()).isEqualTo(SyncStatus.DONE);
        assertThat(after(cancel).message()).isEqualTo("not on the platform");
        verify(bzUpdater, never()).update(any(), any());
        verify(bzWithdrawer, never()).withdraw(any(), any());
    }

    // --- cancel / delete ---

    @Test
    void cancel_withdraws_but_keeps_the_platform_id() throws Exception {
        gigs.save(gig.cancel());
        published.record(BANDZONE, gig.id(), "100");
        queue(gig, BANDZONE, SyncAction.CANCEL);

        dispatcher().runNext();

        verify(bzWithdrawer).withdraw("100", WithdrawAction.CANCEL);
        assertThat(published.externalRef(BANDZONE, gig.id())).contains("100");
    }

    @Test
    void delete_removes_the_copy_and_forgets_its_id_after_the_gig_left_the_catalog() throws Exception {
        published.record(BANDSINTOWN, gig.id(), "900");
        SyncTask task = queue(gig, BANDSINTOWN, SyncAction.DELETE);

        dispatcher().runNext();

        verify(bitWithdrawer).withdraw("900", WithdrawAction.DELETE);
        assertThat(published.isPublished(BANDSINTOWN, gig.id())).isFalse();
        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
    }

    @Test
    void a_failed_delete_keeps_the_platform_id_for_the_retry() throws Exception {
        published.record(BANDSINTOWN, gig.id(), "900");
        doThrow(new GigWithdrawalException("log back in")).when(bitWithdrawer).withdraw(any(), any());
        SyncTask task = queue(gig, BANDSINTOWN, SyncAction.DELETE);

        dispatcher().runNext();

        assertThat(after(task).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(after(task).message()).isEqualTo("log back in");
        assertThat(published.externalRef(BANDSINTOWN, gig.id())).contains("900");
    }

    // --- publish ---

    @Test
    void due_publishes_on_a_platform_run_as_one_batch_and_record_each_id() {
        gigs.save(gig);
        gigs.save(other);
        SyncTask a = queue(gig, BANDZONE, SyncAction.PUBLISH);
        SyncTask b = queue(other, BANDZONE, SyncAction.PUBLISH);
        when(bzPublisher.publishNew(List.of(gig, other))).thenReturn(List.of(
                PublishResult.published(BANDZONE, gig, "100"),
                PublishResult.published(BANDZONE, other, "101")));

        dispatcher().runNext();

        verify(bzPublisher, times(1)).publishNew(anyList());
        assertThat(published.externalRef(BANDZONE, gig.id())).contains("100");
        assertThat(published.externalRef(BANDZONE, other.id())).contains("101");
        assertThat(List.of(after(a).status(), after(b).status())).containsOnly(SyncStatus.DONE);
    }

    @Test
    void a_publish_is_left_out_when_the_gig_was_deleted_cancelled_or_published_meanwhile() {
        Gig cancelled = TestGigs.gig("Cancelled", "Fléda");
        gigs.save(cancelled.cancel());
        gigs.save(other);
        published.record(BANDZONE, other.id(), "101");
        SyncTask deleted = queue(gig, BANDZONE, SyncAction.PUBLISH);
        SyncTask wasCancelled = queue(cancelled, BANDZONE, SyncAction.PUBLISH);
        SyncTask already = queue(other, BANDZONE, SyncAction.PUBLISH);

        dispatcher().runNext();

        verify(bzPublisher, never()).publishNew(anyList());
        assertThat(after(deleted).message()).isEqualTo("the gig was deleted before it was published");
        assertThat(after(wasCancelled).message()).isEqualTo("the gig was cancelled before it was published");
        assertThat(after(already).message()).isEqualTo("already published");
        assertThat(List.of(after(deleted), after(wasCancelled), after(already)))
                .extracting(SyncTask::status).containsOnly(SyncStatus.DONE);
    }

    @Test
    void a_failed_publish_is_not_retried_automatically_and_records_nothing() {
        gigs.save(gig);
        when(bzPublisher.publishNew(anyList()))
                .thenReturn(List.of(PublishResult.failed(BANDZONE, gig, "wizard broke")));
        SyncTask task = queue(gig, BANDZONE, SyncAction.PUBLISH);

        dispatcher().runNext();

        assertThat(after(task).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(task).message()).isEqualTo("wizard broke");
        assertThat(published.isPublished(BANDZONE, gig.id())).isFalse();
    }

    @Test
    void a_publisher_throwing_fails_its_whole_batch() {
        gigs.save(gig);
        gigs.save(other);
        when(bzPublisher.publishNew(anyList())).thenThrow(new IllegalStateException("browser crashed"));
        SyncTask a = queue(gig, BANDZONE, SyncAction.PUBLISH);
        SyncTask b = queue(other, BANDZONE, SyncAction.PUBLISH);

        dispatcher().runNext();

        assertThat(List.of(after(a), after(b))).extracting(SyncTask::status).containsOnly(SyncStatus.FAILED);
        assertThat(published.all()).isEmpty();
    }

    // --- reactivate ---

    @Test
    void reactivating_deletes_the_cancelled_copy_and_recreates_it() throws Exception {
        gigs.save(gig);
        published.record(BANDZONE, gig.id(), "100");
        when(bzPublisher.publishNew(anyList())).thenReturn(List.of(PublishResult.published(BANDZONE, gig, "200")));
        SyncTask task = queue(gig, BANDZONE, SyncAction.REACTIVATE);

        dispatcher().runNext();

        var order = inOrder(bzWithdrawer, bzPublisher);
        order.verify(bzWithdrawer).withdraw("100", WithdrawAction.DELETE);
        order.verify(bzPublisher).publishNew(List.of(gig));
        assertThat(published.externalRef(BANDZONE, gig.id())).contains("200");
        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
    }

    @Test
    void a_failed_delete_while_reactivating_keeps_the_cancelled_copy_and_its_record() throws Exception {
        gigs.save(gig);
        published.record(BANDZONE, gig.id(), "100");
        doThrow(new GigWithdrawalException("login failed")).when(bzWithdrawer).withdraw(any(), any());
        SyncTask task = queue(gig, BANDZONE, SyncAction.REACTIVATE);

        dispatcher().runNext();

        verify(bzPublisher, never()).publishNew(anyList());
        assertThat(published.externalRef(BANDZONE, gig.id())).contains("100");
        assertThat(after(task).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(task).message()).contains("login failed");
    }

    @Test
    void a_failed_recreate_leaves_no_record_so_the_next_publish_creates_it() {
        gigs.save(gig);
        published.record(BANDZONE, gig.id(), "100");
        when(bzPublisher.publishNew(anyList())).thenReturn(List.of(PublishResult.failed(BANDZONE, gig, "wizard broke")));
        SyncTask task = queue(gig, BANDZONE, SyncAction.REACTIVATE);

        dispatcher().runNext();

        assertThat(published.isPublished(BANDZONE, gig.id())).isFalse();
        assertThat(after(task).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(task).message()).contains("wizard broke");
    }

    @Test
    void a_gig_cancelled_again_before_reactivating_ran_is_left_alone() throws Exception {
        gigs.save(gig.cancel());
        published.record(BANDZONE, gig.id(), "100");
        SyncTask task = queue(gig, BANDZONE, SyncAction.REACTIVATE);

        dispatcher().runNext();

        verify(bzWithdrawer, never()).withdraw(any(), any());
        assertThat(after(task).status()).isEqualTo(SyncStatus.DONE);
    }

    // --- restart, discard, wiring ---

    @Test
    void after_a_restart_interrupted_safe_work_runs_again_and_an_interrupted_publish_waits_for_the_user() {
        SyncTask delete = queue(gig, BANDSINTOWN, SyncAction.DELETE);
        SyncTask publish = queue(other, BANDZONE, SyncAction.PUBLISH);
        outbox.markRunning(delete.id(), clock.instant());
        outbox.markRunning(publish.id(), clock.instant());

        dispatcher().recoverInterrupted();

        assertThat(after(delete).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(after(publish).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(after(publish).message()).contains("check there, then Retry or Discard");
    }

    @Test
    void a_discarded_task_is_not_run() throws Exception {
        published.record(BANDZONE, gig.id(), "100");
        SyncTask task = queue(gig, BANDZONE, SyncAction.DELETE);
        outbox.discard(task.id(), clock.instant());

        assertThat(dispatcher().runNext()).isFalse();
        verify(bzWithdrawer, never()).withdraw(any(), any());
    }

    @Test
    void two_strategies_for_the_same_platform_are_rejected() {
        GigWithdrawer second = mock(GigWithdrawer.class);
        when(second.platform()).thenReturn(BANDZONE);

        assertThatThrownBy(() -> new SyncDispatcher(List.of(), List.of(), List.of(bzWithdrawer, second), gigs,
                published, outbox, new SyncFakes.DirectTransactions(), signals, clock))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_refusal_by_the_platform_is_not_retried() throws Exception {
        gigs.save(gig);
        published.record(BANDZONE, gig.id(), "100");
        published.record(BANDSINTOWN, gig.id(), "900");
        doThrow(new GigUpdateException("refused: INVALID_START_TIME", null, true)).when(bzUpdater).update(any(), any());
        doThrow(new GigWithdrawalException("switched off", null, true)).when(bitWithdrawer).withdraw(any(), any());
        SyncTask update = queue(gig, BANDZONE, SyncAction.UPDATE);
        SyncTask delete = queue(gig, BANDSINTOWN, SyncAction.DELETE);

        dispatcher().runNext();
        dispatcher().runNext();

        assertThat(List.of(after(update), after(delete))).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(SyncStatus.FAILED);
            assertThat(t.attempts()).isEqualTo(1);
        });
    }
}
