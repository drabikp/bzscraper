package sk.drabikp.bzscraper.sync.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase.Show;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncCounts;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;

class SyncLogServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final SyncLogService service = new SyncLogService(outbox, signals, signals, clock);
    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private SyncTask task() {
        return outbox.enqueue(gig.id(), SyncTask.labelOf(gig), BANDSINTOWN, SyncAction.PUBLISH, clock.instant());
    }

    @Test
    void a_failed_task_is_retried_from_scratch_and_wakes_the_worker() {
        SyncTask task = task();
        outbox.markRunning(task.id(), clock.instant());
        outbox.markFailed(task.id(), "wizard broke", clock.instant());

        service.retry(task.id());

        assertThat(outbox.get(task.id()).status()).isEqualTo(SyncStatus.PENDING);
        assertThat(outbox.get(task.id()).attempts()).isZero();
        assertThat(signals.wakes).isEqualTo(1);
    }

    @Test
    void only_failed_or_discarded_tasks_can_be_retried_and_only_pending_or_failed_discarded() {
        SyncTask pending = task();
        SyncTask running = task();
        outbox.markRunning(running.id(), clock.instant());

        assertThatThrownBy(() -> service.retry(pending.id())).isInstanceOf(UserFacingException.class);
        assertThatThrownBy(() -> service.discard(running.id())).isInstanceOf(UserFacingException.class);

        service.discard(pending.id());
        assertThat(outbox.get(pending.id()).status()).isEqualTo(SyncStatus.DISCARDED);
        service.retry(pending.id());
        assertThat(outbox.get(pending.id()).status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void the_log_of_a_task_lists_what_happened_to_it() {
        SyncTask task = task();
        outbox.markRunning(task.id(), clock.instant());
        outbox.markDone(task.id(), null, clock.instant());

        assertThat(service.log(task.id())).hasSize(3);
        assertThat(service.unfinished()).isEmpty();
        assertThat(service.recent(10)).extracting(SyncTask::id).containsExactly(task.id());
    }

    @Test
    void the_lists_show_the_latest_the_open_or_the_failed_tasks_newest_first_and_count_the_open_ones() {
        SyncTask queued = task();
        SyncTask retrying = task();
        outbox.markRunning(retrying.id(), clock.instant());
        outbox.markRetry(retrying.id(), "timeout", clock.instant().plusSeconds(60), clock.instant());
        SyncTask running = task();
        outbox.markRunning(running.id(), clock.instant());
        SyncTask failed = task();
        outbox.markRunning(failed.id(), clock.instant());
        outbox.markFailed(failed.id(), "refused", clock.instant());
        SyncTask done = task();
        outbox.markRunning(done.id(), clock.instant());
        outbox.markDone(done.id(), null, clock.instant());

        assertThat(service.tasks(Show.ALL)).extracting(SyncTask::id)
                .containsExactly(done.id(), failed.id(), running.id(), retrying.id(), queued.id());
        assertThat(service.tasks(Show.OPEN)).extracting(SyncTask::id)
                .containsExactly(failed.id(), running.id(), retrying.id(), queued.id());
        assertThat(service.tasks(Show.FAILED)).extracting(SyncTask::id).containsExactly(failed.id());
        assertThat(service.counts()).isEqualTo(new SyncCounts(1, 1, 1, 1));
    }
}
