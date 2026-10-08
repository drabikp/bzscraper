package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDSINTOWN;
import static sk.drabikp.bzscraper.domain.model.Platform.BANDZONE;

class GigPublishingServiceTest {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.DirectTransactions transactions = new SyncFakes.DirectTransactions();
    private final GigPublishingService service = new GigPublishingService(
            new SyncRequests(outbox, published, signals, signals, new SyncFakes.MutableClock()), transactions);

    private final Gig a = TestGigs.gig("A", "Klub 007");
    private final Gig b = TestGigs.gig("B", "Barrák");

    @Test
    void queues_one_publish_per_gig_and_platform_in_one_transaction_and_wakes_the_worker() {
        QueueResult result = service.publish(EnumSet.allOf(Platform.class),
                List.of(a, b));

        assertThat(result.queued()).extracting(SyncTask::platform, SyncTask::action).containsExactly(
                tuple(BANDZONE, SyncAction.PUBLISH), tuple(BANDZONE, SyncAction.PUBLISH),
                tuple(BANDSINTOWN, SyncAction.PUBLISH), tuple(BANDSINTOWN, SyncAction.PUBLISH));
        assertThat(transactions.used).isEqualTo(1);
        assertThat(signals.wakes).isEqualTo(1);
    }

    @Test
    void published_queued_and_cancelled_gigs_are_left_out_with_the_reason() {
        published.record(BANDZONE, a.id(), "100");
        service.publish(Set.of(BANDZONE), List.of(b));
        Gig cancelled = TestGigs.gig("C", "Fléda").cancel();

        QueueResult result = service.publish(Set.of(BANDZONE), List.of(a, b, cancelled));

        assertThat(result.queued()).isEmpty();
        assertThat(result.notQueued()).containsExactly("A on Bandzone: already published",
                "B on Bandzone: already being published", "C on Bandzone: cancelled");
    }

    @Test
    void nothing_queued_does_not_wake_the_worker() {
        published.record(BANDZONE, a.id(), "100");

        service.publish(Set.of(BANDZONE), List.of(a));

        assertThat(signals.wakes).isZero();
    }
}
