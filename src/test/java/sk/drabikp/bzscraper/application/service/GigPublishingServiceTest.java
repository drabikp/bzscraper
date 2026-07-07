package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GigPublishingServiceTest {

    private final PublishedGigStore store = mock(PublishedGigStore.class);

    private static GigPublisher publisherFor(Platform platform) {
        GigPublisher p = mock(GigPublisher.class);
        when(p.platform()).thenReturn(platform);
        return p;
    }

    @Test
    void records_only_confirmed_gigs_with_their_external_ref() {
        Gig a = TestGigs.gig("A", "Klub 007");
        Gig b = TestGigs.gig("B", "Barrák");
        GigPublisher bit = publisherFor(Platform.BANDSINTOWN);
        when(bit.publishNew(anyList())).thenReturn(List.of(
                PublishResult.published(Platform.BANDSINTOWN, a, "evt-42"),
                PublishResult.failed(Platform.BANDSINTOWN, b, "row rejected")));
        GigPublishingService service = new GigPublishingService(List.of(bit), store);

        List<PublishResult> results = service.publish(Platform.BANDSINTOWN, List.of(a, b));

        assertThat(results).extracting(PublishResult::status)
                .containsExactlyInAnyOrder(PublishStatus.PUBLISHED, PublishStatus.FAILED);
        verify(store).record(Platform.BANDSINTOWN, a.id(), "evt-42");
        verify(store, never()).record(Platform.BANDSINTOWN, b.id(), null);
    }

    @Test
    void already_published_gigs_are_skipped_before_the_publisher_is_called() {
        Gig already = TestGigs.gig("Old", "Klub 007");
        Gig fresh = TestGigs.gig("Fresh", "New Venue");
        when(store.isPublished(Platform.BANDSINTOWN, already.id())).thenReturn(true);
        GigPublisher bit = publisherFor(Platform.BANDSINTOWN);
        when(bit.publishNew(anyList())).thenReturn(List.of(PublishResult.published(Platform.BANDSINTOWN, fresh)));
        GigPublishingService service = new GigPublishingService(List.of(bit), store);

        List<PublishResult> results = service.publish(Platform.BANDSINTOWN, List.of(already, fresh));

        assertThat(results).extracting(PublishResult::status).containsExactlyInAnyOrder(
                PublishStatus.SKIPPED_ALREADY_UPLOADED, PublishStatus.PUBLISHED);
        verify(bit).publishNew(List.of(fresh));
    }

    @Test
    void when_everything_is_already_published_the_publisher_is_not_called_and_nothing_recorded() {
        Gig already = TestGigs.gig("Old", "Klub 007");
        when(store.isPublished(Platform.BANDSINTOWN, already.id())).thenReturn(true);
        GigPublisher bit = publisherFor(Platform.BANDSINTOWN);
        GigPublishingService service = new GigPublishingService(List.of(bit), store);

        service.publish(Platform.BANDSINTOWN, List.of(already));

        verify(bit, never()).publishNew(anyList());
        verify(store, never()).record(any(), any(), any());
    }

    @Test
    void a_publisher_throwing_fails_only_its_own_batch_and_records_nothing() {
        Gig a = TestGigs.gig("A", "Klub 007");
        GigPublisher bit = publisherFor(Platform.BANDSINTOWN);
        when(bit.publishNew(anyList())).thenThrow(new RuntimeException("driver crashed"));
        GigPublishingService service = new GigPublishingService(List.of(bit), store);

        List<PublishResult> results = service.publish(Platform.BANDSINTOWN, List.of(a));

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(PublishStatus.FAILED);
            assertThat(r.detail()).isEqualTo("driver crashed");
        });
        verify(store, never()).record(any(), any(), any());
    }

    @Test
    void publishes_to_multiple_platforms_and_records_each() {
        Gig a = TestGigs.gig("A", "Klub 007");
        GigPublisher bz = publisherFor(Platform.BANDZONE);
        GigPublisher bit = publisherFor(Platform.BANDSINTOWN);
        when(bz.publishNew(anyList())).thenReturn(List.of(PublishResult.published(Platform.BANDZONE, a, "561859")));
        when(bit.publishNew(anyList())).thenReturn(List.of(PublishResult.published(Platform.BANDSINTOWN, a)));
        GigPublishingService service = new GigPublishingService(List.of(bz, bit), store);

        List<PublishResult> results = service.publish(Set.of(Platform.BANDZONE, Platform.BANDSINTOWN), List.of(a));

        assertThat(results).extracting(PublishResult::platform)
                .containsExactlyInAnyOrder(Platform.BANDZONE, Platform.BANDSINTOWN);
        verify(store).record(Platform.BANDZONE, a.id(), "561859");
        verify(store).record(Platform.BANDSINTOWN, a.id(), null);
    }

    @Test
    void targeting_a_platform_with_no_registered_publisher_fails_loudly() {
        GigPublishingService service = new GigPublishingService(
                List.of(publisherFor(Platform.BANDSINTOWN)), store);

        assertThatThrownBy(() -> service.publish(Platform.BANDZONE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registering_two_publishers_for_the_same_platform_is_rejected() {
        assertThatThrownBy(() -> new GigPublishingService(
                List.of(publisherFor(Platform.BANDSINTOWN), publisherFor(Platform.BANDSINTOWN)), store))
                .isInstanceOf(IllegalStateException.class);
    }
}
