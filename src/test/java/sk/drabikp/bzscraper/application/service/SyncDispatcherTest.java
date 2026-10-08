package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepType;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDSINTOWN;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDZONE;

/** The worker's entry: the next due task to the engine; settling what a restart interrupted. */
class SyncDispatcherTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final SyncFakes.Step bzRemove = new SyncFakes.Step(BANDZONE, StepType.REMOVE, 1);
    private final SyncDispatcher dispatcher = new SyncDispatcher(new WorkflowEngine(List.of(bzRemove), gigs,
            published, outbox, new SyncFakes.DirectTransactions(), signals, clock), outbox, signals, clock);

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private SyncTask queue(Gig g, Platform platform, SyncAction action) {
        return outbox.enqueue(g.id(), SyncTask.labelOf(g), platform, action, clock.instant());
    }

    @Test
    void the_next_due_task_runs_until_nothing_is_due() {
        published.record(BANDZONE, gig.id(), "100");
        SyncTask delete = queue(gig, BANDZONE, SyncAction.DELETE);

        assertThat(dispatcher.runNext()).isTrue();
        assertThat(dispatcher.runNext()).isFalse();

        assertThat(outbox.find(delete.id()).orElseThrow().status()).isEqualTo(SyncStatus.DONE);
        assertThat(bzRemove.ran()).singleElement().satisfies(i -> assertThat(i.externalRef()).isEqualTo("100"));
    }

    @Test
    void a_discarded_task_is_not_run() {
        published.record(BANDZONE, gig.id(), "100");
        SyncTask task = queue(gig, BANDZONE, SyncAction.DELETE);
        outbox.discard(task.id(), clock.instant());

        assertThat(dispatcher.runNext()).isFalse();
        assertThat(bzRemove.calls).isEmpty();
    }

    @Test
    void after_a_restart_interrupted_safe_work_runs_again_and_an_interrupted_publish_waits_for_the_user() {
        SyncTask delete = queue(gig, BANDSINTOWN, SyncAction.DELETE);
        SyncTask publish = queue(TestGigs.gig("Other", "Barrák"), BANDZONE, SyncAction.PUBLISH);
        outbox.markRunning(delete.id(), clock.instant());
        outbox.markRunning(publish.id(), clock.instant());

        dispatcher.recoverInterrupted();

        assertThat(outbox.find(delete.id()).orElseThrow().status()).isEqualTo(SyncStatus.PENDING);
        assertThat(outbox.find(publish.id()).orElseThrow().status()).isEqualTo(SyncStatus.FAILED);
        assertThat(outbox.find(publish.id()).orElseThrow().message()).contains("check there, then Retry or Discard");
    }

    @Test
    void a_paused_sync_starts_nothing_until_resumed() {
        published.record(BANDZONE, gig.id(), "100");
        queue(gig, BANDZONE, SyncAction.DELETE);
        SyncPause pause = new SyncPause(signals, signals);
        SyncDispatcher pausable = new SyncDispatcher(new WorkflowEngine(List.of(bzRemove), gigs, published, outbox,
                new SyncFakes.DirectTransactions(), signals, clock, pause::paused), outbox, signals, clock, pause);

        pause.pause();
        assertThat(pausable.runNext()).isFalse();
        assertThat(bzRemove.calls).isEmpty();

        pause.resume();
        assertThat(pausable.runNext()).isTrue();
        assertThat(signals.wakes).as("resuming wakes the worker").isPositive();
    }
}
