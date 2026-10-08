package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDZONE;

class GigResyncServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final GigResyncService service = new GigResyncService(gigs,
            new SyncRequests(outbox, published, signals, signals, new SyncFakes.MutableClock()),
            new SyncFakes.DirectTransactions());

    @Test
    void queues_an_update_where_the_gig_is_published_and_says_which_gigs_had_nothing_to_do() {
        Gig onBandzone = TestGigs.gig("On Bandzone", "Klub 007");
        Gig local = TestGigs.gig("Local", "Barrák");
        gigs.save(onBandzone);
        gigs.save(local);
        published.record(BANDZONE, onBandzone.id(), "100");

        QueueResult result = service.resync(List.of(onBandzone.id(), local.id()));

        assertThat(result.queued()).extracting(SyncTask::action).containsExactly(SyncAction.UPDATE);
        assertThat(result.notQueued()).singleElement().asString().startsWith("Local:");
        assertThat(signals.wakes).isEqualTo(1);
    }
}
