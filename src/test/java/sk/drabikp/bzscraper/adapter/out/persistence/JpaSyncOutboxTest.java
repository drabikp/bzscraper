package sk.drabikp.bzscraper.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncLogEntry;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDSINTOWN;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDZONE;

@SpringBootTest
@Transactional
class JpaSyncOutboxTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    @Autowired
    private JpaSyncOutbox outbox;

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");
    private final Gig other = TestGigs.gig("Other", "Barrák");

    private SyncTask queue(Gig g, Platform platform, SyncAction action) {
        return outbox.enqueue(g.id(), SyncTask.labelOf(g), platform, action, NOW);
    }

    @Test
    void a_queued_task_is_pending_due_now_and_logged() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.UPDATE);

        assertThat(task.status()).isEqualTo(SyncStatus.PENDING);
        assertThat(task.gigId()).isEqualTo(gig.id());
        assertThat(outbox.nextDue(NOW)).contains(task);
        assertThat(outbox.openFor(gig.id())).containsExactly(task);
        assertThat(outbox.log(task.id())).extracting(SyncLogEntry::message).containsExactly("queued");
    }

    @Test
    void a_task_runs_once_and_every_step_is_logged() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.DELETE);

        assertThat(outbox.markRunning(task.id(), NOW)).isTrue();
        assertThat(outbox.markRunning(task.id(), NOW)).as("already running").isFalse();
        outbox.markRetry(task.id(), "timeout", NOW.plus(Duration.ofMinutes(1)), NOW);
        assertThat(outbox.nextDue(NOW)).as("not due before the retry time").isEmpty();
        assertThat(outbox.nextDue(NOW.plus(Duration.ofMinutes(1)))).isPresent();
        outbox.markRunning(task.id(), NOW.plus(Duration.ofMinutes(1)));
        outbox.markDone(task.id(), null, NOW.plus(Duration.ofMinutes(2)));

        SyncTask done = outbox.find(task.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(SyncStatus.DONE);
        assertThat(done.attempts()).isEqualTo(2);
        assertThat(outbox.log(task.id())).extracting(SyncLogEntry::message).containsExactly(
                "queued", "attempt 1 started", "failed: timeout — will retry at 2026-10-08T10:01:00Z",
                "attempt 2 started", "done");
    }

    @Test
    void a_discarded_task_cannot_be_started_and_a_retry_starts_it_afresh() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.PUBLISH);
        outbox.markRunning(task.id(), NOW);
        outbox.markFailed(task.id(), "wizard broke", NOW);
        assertThat(outbox.unfinished()).extracting(SyncTask::id).contains(task.id());

        outbox.discard(task.id(), NOW);
        assertThat(outbox.markRunning(task.id(), NOW)).isFalse();
        outbox.requeue(task.id(), NOW);

        SyncTask again = outbox.find(task.id()).orElseThrow();
        assertThat(again.status()).isEqualTo(SyncStatus.PENDING);
        assertThat(again.attempts()).isZero();
    }

    @Test
    void due_publishes_of_a_platform_come_together_oldest_first() {
        SyncTask a = queue(gig, BANDSINTOWN, SyncAction.PUBLISH);
        queue(gig, BANDZONE, SyncAction.PUBLISH);
        SyncTask b = queue(other, BANDSINTOWN, SyncAction.PUBLISH);

        assertThat(outbox.due(BANDSINTOWN, SyncAction.PUBLISH, NOW)).extracting(SyncTask::id)
                .containsExactly(a.id(), b.id());
    }

    @Test
    void superseding_discards_only_the_pending_tasks_of_that_gig_on_that_platform() {
        SyncTask pending = queue(gig, BANDZONE, SyncAction.UPDATE);
        SyncTask running = queue(gig, BANDZONE, SyncAction.CANCEL);
        outbox.markRunning(running.id(), NOW);
        SyncTask elsewhere = queue(gig, BANDSINTOWN, SyncAction.UPDATE);

        assertThat(outbox.supersede(gig.id(), BANDZONE, "the gig was deleted", NOW)).extracting(SyncTask::id)
                .containsExactly(pending.id());
        assertThat(outbox.find(pending.id()).orElseThrow().status()).isEqualTo(SyncStatus.DISCARDED);
        assertThat(outbox.find(running.id()).orElseThrow().status()).isEqualTo(SyncStatus.RUNNING);
        assertThat(outbox.find(elsewhere.id()).orElseThrow().status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void tasks_follow_a_gig_whose_identity_changed() {
        SyncTask task = queue(gig, BANDZONE, SyncAction.UPDATE);
        Gig moved = TestGigs.gig("Fest", "Klub 007",
                ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

        outbox.move(gig.id(), moved.id(), SyncTask.labelOf(moved));

        assertThat(outbox.openFor(moved.id())).extracting(SyncTask::id).containsExactly(task.id());
        assertThat(outbox.openFor(gig.id())).isEmpty();
    }

    @Test
    void recent_lists_the_newest_first() {
        SyncTask first = queue(gig, BANDZONE, SyncAction.UPDATE);
        SyncTask second = queue(other, BANDZONE, SyncAction.UPDATE);

        assertThat(outbox.recent(1)).extracting(SyncTask::id).containsExactly(second.id());
        assertThat(outbox.recent(10)).extracting(SyncTask::id).containsSubsequence(second.id(), first.id());
    }
}
