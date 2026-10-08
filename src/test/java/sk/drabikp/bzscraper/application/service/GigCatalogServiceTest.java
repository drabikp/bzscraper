package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Publication;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDSINTOWN;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDZONE;

/** The catalog changes and the platform work they queue (outbox: one transaction). */
class GigCatalogServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.DirectTransactions transactions = new SyncFakes.DirectTransactions();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final GigCatalogService service = new GigCatalogService(gigs, published, transactions,
            new SyncRequests(outbox, published, signals, signals, clock));

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    private Gig onBothPlatforms(Gig g) {
        gigs.save(g);
        published.record(BANDZONE, g.id(), "100");
        published.record(BANDSINTOWN, g.id(), "900");
        return g;
    }

    private static Gig renamed(Gig g, String title) {
        return new Gig(title, g.schedule(), g.location(), g.lineup(), g.admission(), null, null, null, null,
                g.cancelled());
    }

    private List<SyncTask> pending() {
        return outbox.all().stream().filter(t -> t.status() == SyncStatus.PENDING).toList();
    }

    @Test
    void an_edit_queues_an_update_on_every_platform_the_gig_is_on_in_the_same_transaction() {
        onBothPlatforms(gig);

        QueueResult result = service.update(gig.id(), renamed(gig, "Fest 2026"));

        assertThat(transactions.used).isEqualTo(1);
        assertThat(gigs.findById(gig.id())).get().extracting(Gig::title).isEqualTo("Fest 2026");
        assertThat(result.queued()).extracting(SyncTask::platform, SyncTask::action)
                .containsExactly(tuple(BANDZONE, SyncAction.UPDATE), tuple(BANDSINTOWN, SyncAction.UPDATE));
        assertThat(signals.wakes).isEqualTo(1);
    }

    @Test
    void an_edit_without_changes_or_of_a_gig_on_no_platform_queues_nothing() {
        onBothPlatforms(gig);
        Gig local = TestGigs.gig("Local only", "Barrák");
        gigs.save(local);

        assertThat(service.update(gig.id(), gig).queued()).isEmpty();
        assertThat(service.update(local.id(), renamed(local, "Renamed")).queued()).isEmpty();
        assertThat(signals.wakes).isZero();
    }

    @Test
    void edits_made_before_the_update_ran_are_carried_by_that_one_update() {
        onBothPlatforms(gig);

        service.update(gig.id(), renamed(gig, "Fest 2026"));
        service.update(gig.id(), renamed(gig, "Fest 2026 — sold out"));

        assertThat(pending()).hasSize(2);
    }

    @Test
    void an_edit_while_the_update_is_already_running_queues_another() {
        onBothPlatforms(gig);
        SyncTask first = service.update(gig.id(), renamed(gig, "Fest 2026")).queued().getFirst();
        outbox.markRunning(first.id(), clock.instant());

        QueueResult second = service.update(gig.id(), renamed(gig, "Fest 2026 — sold out"));

        assertThat(second.queued()).extracting(SyncTask::platform).contains(BANDZONE);
    }

    @Test
    void an_edit_that_moves_the_gigs_identity_takes_its_records_and_pending_work_along() {
        onBothPlatforms(gig);
        service.update(gig.id(), renamed(gig, "Fest 2026"));
        Gig moved = TestGigs.gig("Fest 2026", "Klub 007",
                ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

        service.update(gig.id(), moved);

        assertThat(gigs.findById(gig.id())).isEmpty();
        assertThat(published.externalRef(BANDZONE, moved.id())).contains("100");
        assertThat(pending()).hasSize(2).allSatisfy(t -> {
            assertThat(t.gigId()).isEqualTo(moved.id());
            assertThat(t.gigLabel()).startsWith("2026-10-01");
        });
    }

    @Test
    void cancel_and_reactivate_queue_the_same_on_the_platforms() {
        onBothPlatforms(gig);

        QueueResult cancelled = service.cancel(gig.id());
        outbox.all().forEach(t -> outbox.markDone(t.id(), null, clock.instant()));
        QueueResult reactivated = service.reactivate(gig.id());

        assertThat(cancelled.queued()).extracting(SyncTask::action).containsOnly(SyncAction.CANCEL).hasSize(2);
        assertThat(reactivated.queued()).extracting(SyncTask::action).containsOnly(SyncAction.REACTIVATE).hasSize(2);
        assertThat(gigs.findById(gig.id())).get().extracting(Gig::cancelled).isEqualTo(false);
    }

    @Test
    void a_cancel_and_a_reactivate_that_both_have_not_run_cancel_out_into_an_update() {
        onBothPlatforms(gig);

        service.cancel(gig.id());
        QueueResult reactivated = service.reactivate(gig.id());

        assertThat(reactivated.queued()).extracting(SyncTask::action).containsOnly(SyncAction.UPDATE);
        assertThat(pending()).extracting(SyncTask::action).containsOnly(SyncAction.UPDATE);
        assertThat(outbox.all()).filteredOn(t -> t.action() == SyncAction.CANCEL)
                .extracting(SyncTask::status).containsOnly(SyncStatus.DISCARDED);
    }

    @Test
    void cancelling_a_cancelled_gig_or_an_unknown_one_changes_nothing() {
        onBothPlatforms(gig.cancel());

        assertThat(service.cancel(gig.id()).queued()).isEmpty();
        assertThat(service.cancel(TestGigs.gig("Unknown", "Nowhere").id()).queued()).isEmpty();
        assertThat(outbox.all()).isEmpty();
    }

    @Test
    void delete_discards_pending_work_and_queues_a_delete_where_the_gig_is_while_keeping_its_records() {
        onBothPlatforms(gig);
        service.update(gig.id(), renamed(gig, "Fest 2026"));

        QueueResult deleted = service.delete(gig.id());

        assertThat(gigs.findById(gig.id())).isEmpty();
        assertThat(deleted.queued()).extracting(SyncTask::action).containsOnly(SyncAction.DELETE).hasSize(2);
        assertThat(deleted.queued()).allSatisfy(t -> assertThat(t.gigLabel()).contains("Fest 2026"));
        assertThat(outbox.all()).filteredOn(t -> t.action() == SyncAction.UPDATE)
                .extracting(SyncTask::status).containsOnly(SyncStatus.DISCARDED);
        assertThat(published.externalRef(BANDSINTOWN, gig.id())).as("kept until the platform delete succeeds")
                .contains("900");
    }

    @Test
    void deleting_a_gig_on_no_platform_queues_nothing() {
        gigs.save(gig);

        assertThat(service.delete(gig.id()).queued()).isEmpty();
        assertThat(signals.wakes).isZero();
    }

    @Test
    void publications_are_grouped_by_gig_and_platform() {
        onBothPlatforms(gig);

        assertThat(service.publicationsByGig().get(gig.id())).containsOnlyKeys(BANDZONE, BANDSINTOWN)
                .containsEntry(BANDZONE, new Publication(BANDZONE, gig.id(), "100"));
    }
}
