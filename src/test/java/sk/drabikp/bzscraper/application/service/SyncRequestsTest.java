package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.GigUpdater;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformSupport;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDSINTOWN;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDZONE;

/** Queueing rules: a new task replaces failed ones; work a platform's adapters don't take on past gigs is left out. */
class SyncRequestsTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();        // 2026-10-08
    private final GigUpdater bzUpdater = adapter(GigUpdater.class, BANDZONE);
    private final GigUpdater bitUpdater = adapter(GigUpdater.class, BANDSINTOWN);
    private final GigWithdrawer bzWithdrawer = adapter(GigWithdrawer.class, BANDZONE);
    private final GigWithdrawer bitWithdrawer = adapter(GigWithdrawer.class, BANDSINTOWN);
    private final GigPublisher bzPublisher = adapter(GigPublisher.class, BANDZONE);
    private final GigPublisher bitPublisher = adapter(GigPublisher.class, BANDSINTOWN);
    /** As the adapters declare it: Bandsintown publishes past events but doesn't edit, cancel or remove them. */
    private final PlatformSupport support;
    private final SyncRequests requests;
    private final GigCatalogService catalog;
    private final GigPublishingService publishing;

    SyncRequestsTest() {
        when(bzUpdater.updatesPastEvents()).thenReturn(true);
        when(bzWithdrawer.withdrawsPastEvents(any())).thenReturn(true);
        when(bzPublisher.publishesPastEvents()).thenReturn(true);
        when(bitPublisher.publishesPastEvents()).thenReturn(true);
        support = AdapterCapabilities.of(List.of(bzPublisher, bitPublisher), List.of(bzUpdater, bitUpdater),
                List.of(bzWithdrawer, bitWithdrawer));
        requests = new SyncRequests(outbox, published, signals, signals, clock, support);
        catalog = new GigCatalogService(gigs, published, new CalendarFakes.Links(), new SyncFakes.DirectTransactions(),
                requests);
        publishing = new GigPublishingService(requests, new SyncFakes.DirectTransactions());
    }

    private static <T> T adapter(Class<T> type, Platform platform) {
        T adapter = mock(type);
        if (adapter instanceof GigUpdater u) {
            when(u.platform()).thenReturn(platform);
        } else if (adapter instanceof GigWithdrawer w) {
            when(w.platform()).thenReturn(platform);
        } else if (adapter instanceof GigPublisher p) {
            when(p.platform()).thenReturn(platform);
        }
        return adapter;
    }

    private final Gig past = onBoth(TestGigs.gig("Summer Fest", "Klub 007"));             // 2026-09-15
    private final Gig upcoming = onBoth(TestGigs.gig("Winter Fest", "Barrák",
            ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0, ZoneId.of("Europe/Prague"))));

    private Gig onBoth(Gig gig) {
        gigs.save(gig);
        published.record(BANDZONE, gig.id(), "bz-" + gig.title());
        published.record(BANDSINTOWN, gig.id(), "bit-" + gig.title());
        return gig;
    }

    private static Gig renamed(Gig g) {
        return new Gig(g.title() + " 2026", g.schedule(), g.location(), g.lineup(), g.admission(), null, null, null,
                null, g.cancelled());
    }

    @Test
    void a_new_publish_replaces_the_failed_one_so_it_stops_waiting_for_the_user() {
        Gig fresh = TestGigs.gig("SNP", "Secret Garden");
        gigs.save(fresh);
        SyncTask failed = publishing.publish(Set.of(BANDZONE), List.of(fresh)).queued().getFirst();
        outbox.markRunning(failed.id(), clock.instant());
        outbox.markFailed(failed.id(), "Bandzone doesn't know the city", clock.instant());

        SyncTask again = publishing.publish(Set.of(BANDZONE), List.of(fresh)).queued().getFirst();

        assertThat(outbox.find(failed.id())).get().satisfies(t -> {
            assertThat(t.status()).isEqualTo(SyncStatus.DISCARDED);
            assertThat(t.message()).contains("replaced", "#" + again.id());
        });
        assertThat(outbox.unfinished()).extracting(SyncTask::id).containsExactly(again.id());
    }

    @Test
    void an_edit_of_a_past_gig_is_not_tried_where_the_platform_refuses_past_edits() {
        QueueResult pastEdit = catalog.update(past.id(), renamed(past));
        QueueResult upcomingEdit = catalog.update(upcoming.id(), renamed(upcoming));

        assertThat(pastEdit.queued()).extracting(SyncTask::platform).containsExactly(BANDZONE);
        assertThat(pastEdit.notQueued()).singleElement().asString()
                .contains("Bandsintown", "doesn't take changes to past events");
        assertThat(upcomingEdit.queued()).extracting(SyncTask::platform).containsExactly(BANDZONE, BANDSINTOWN);
    }

    @Test
    void cancelling_a_past_gig_is_left_out_where_the_platform_cant_and_deleting_it_forgets_the_copy() {
        QueueResult cancelled = catalog.cancel(past.id());
        QueueResult deleted = catalog.delete(past.id());

        assertThat(cancelled.queued()).extracting(SyncTask::platform).containsExactly(BANDZONE);
        assertThat(cancelled.notQueued()).singleElement().asString().contains("can't be cancelled from here");
        assertThat(deleted.queued()).extracting(SyncTask::platform, SyncTask::action)
                .containsExactly(tuple(BANDZONE, SyncAction.DELETE));
        assertThat(deleted.notQueued()).singleElement().asString().contains("delete it there by hand");
        assertThat(published.isPublished(BANDSINTOWN, past.id())).as("nothing left to track").isFalse();
    }

    @Test
    void a_task_queued_before_the_gig_was_over_is_not_tried_when_it_runs() throws Exception {
        SyncRequests everything = new SyncRequests(outbox, published, signals, signals, clock);
        SyncTask update = new GigCatalogService(gigs, published, new CalendarFakes.Links(),
                new SyncFakes.DirectTransactions(), everything).update(past.id(), renamed(past)).queued().stream()
                .filter(t -> t.platform() == BANDSINTOWN).findFirst().orElseThrow();
        SyncDispatcher dispatcher = new SyncDispatcher(List.of(bzPublisher, bitPublisher),
                List.of(bzUpdater, bitUpdater), List.of(bzWithdrawer, bitWithdrawer), gigs, published, outbox,
                new SyncFakes.DirectTransactions(), signals, clock);

        while (dispatcher.runNext()) {
            // run everything due
        }

        assertThat(outbox.find(update.id())).get().satisfies(t -> {
            assertThat(t.status()).isEqualTo(SyncStatus.DONE);
            assertThat(t.message()).contains("left as it is", "Bandsintown");
        });
        verify(bitUpdater, never()).update(any(), any());
    }
}
