package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.GigUpdateException;
import sk.drabikp.bzscraper.application.port.out.GigUpdater;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformResult;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GigResyncServiceTest {

    private final PublishedGigStore store = mock(PublishedGigStore.class);
    private final GigPublisher bzPublisher = mock(GigPublisher.class);
    private final GigUpdater bzUpdater = mock(GigUpdater.class);
    private final GigWithdrawer bzWithdrawer = mock(GigWithdrawer.class);

    private final Gig original = TestGigs.gig("A", "Klub 007");
    private final GigId originalId = original.id();
    private final Gig movedToNewDate = TestGigs.gig("A", "Klub 007",
            ZonedDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

    GigResyncServiceTest() {
        when(bzPublisher.platform()).thenReturn(Platform.BANDZONE);
        when(bzUpdater.platform()).thenReturn(Platform.BANDZONE);
        when(bzWithdrawer.platform()).thenReturn(Platform.BANDZONE);
    }

    private GigResyncService service() {
        return new GigResyncService(List.of(bzPublisher), List.of(bzUpdater), List.of(bzWithdrawer), store);
    }

    private void publishedOnBandzone(GigId id, String ref) {
        when(store.isPublished(Platform.BANDZONE, id)).thenReturn(true);
        when(store.externalRef(Platform.BANDZONE, id)).thenReturn(Optional.ofNullable(ref));
    }

    // --- edit ---

    @Test
    void an_edit_updates_the_platform_copy_in_place() throws Exception {
        Gig renamed = TestGigs.gig("A renamed", "Klub 007");
        publishedOnBandzone(originalId, "100");

        List<PlatformResult> results = service().pushEdit(originalId, renamed);

        verify(bzUpdater).update("100", renamed);
        verify(bzWithdrawer, never()).withdraw(any(), any());
        verify(bzPublisher, never()).publishNew(anyList());
        verify(store, never()).remove(any(), any()); // same identity, same ref: nothing to move
        assertThat(results).singleElement().satisfies(r -> assertThat(r.succeeded()).isTrue());
    }

    @Test
    void an_edit_that_changes_identity_moves_the_record_and_keeps_the_ref() throws Exception {
        publishedOnBandzone(originalId, "100");

        service().pushEdit(originalId, movedToNewDate);

        verify(store).remove(Platform.BANDZONE, originalId);
        verify(store).record(Platform.BANDZONE, movedToNewDate.id(), "100");
        verify(bzUpdater).update("100", movedToNewDate);
    }

    @Test
    void a_failed_update_is_reported_but_the_record_still_follows_the_gig() throws Exception {
        publishedOnBandzone(originalId, "100");
        doThrow(new GigUpdateException("form rejected")).when(bzUpdater).update(any(), any());

        List<PlatformResult> results = service().pushEdit(originalId, movedToNewDate);

        verify(store).record(Platform.BANDZONE, movedToNewDate.id(), "100");
        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.succeeded()).isFalse();
            assertThat(r.detail()).isEqualTo("form rejected");
        });
    }

    @Test
    void an_edit_without_a_captured_ref_moves_the_record_and_reports_not_updated() throws Exception {
        publishedOnBandzone(originalId, null);

        List<PlatformResult> results = service().pushEdit(originalId, movedToNewDate);

        verify(bzUpdater, never()).update(any(), any());
        verify(store).record(Platform.BANDZONE, movedToNewDate.id(), null);
        assertThat(results).singleElement().satisfies(r -> assertThat(r.succeeded()).isFalse());
    }

    @Test
    void an_edit_on_a_platform_without_an_updater_is_reported_not_updated() {
        when(store.isPublished(Platform.BANDSINTOWN, originalId)).thenReturn(true);
        when(store.externalRef(Platform.BANDSINTOWN, originalId)).thenReturn(Optional.empty());

        List<PlatformResult> results = service().pushEdit(originalId, original);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.platform()).isEqualTo(Platform.BANDSINTOWN);
            assertThat(r.succeeded()).isFalse();
        });
    }

    @Test
    void platforms_where_the_gig_was_never_published_are_skipped() throws Exception {
        assertThat(service().pushEdit(originalId, movedToNewDate)).isEmpty();
        assertThat(service().reactivate(original)).isEmpty();
        verify(bzUpdater, never()).update(any(), any());
        verify(bzPublisher, never()).publishNew(anyList());
    }

    // --- reactivate ---

    @Test
    void reactivating_deletes_the_cancelled_copy_and_recreates_it() throws Exception {
        publishedOnBandzone(originalId, "100");
        when(bzPublisher.publishNew(anyList()))
                .thenReturn(List.of(PublishResult.published(Platform.BANDZONE, original, "200")));

        List<PlatformResult> results = service().reactivate(original);

        var order = inOrder(bzWithdrawer, bzPublisher, store);
        order.verify(bzWithdrawer).withdraw("100", WithdrawAction.DELETE);
        order.verify(store).remove(Platform.BANDZONE, originalId);
        order.verify(bzPublisher).publishNew(List.of(original));
        order.verify(store).record(Platform.BANDZONE, originalId, "200");
        assertThat(results).singleElement().satisfies(r -> assertThat(r.succeeded()).isTrue());
    }

    @Test
    void a_failed_delete_keeps_the_cancelled_copy_and_its_record() throws Exception {
        publishedOnBandzone(originalId, "100");
        doThrow(new GigWithdrawalException("login failed")).when(bzWithdrawer).withdraw(any(), any());

        List<PlatformResult> results = service().reactivate(original);

        verify(bzPublisher, never()).publishNew(anyList());
        verify(store, never()).remove(any(), any());
        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.succeeded()).isFalse();
            assertThat(r.detail()).contains("login failed");
        });
    }

    @Test
    void a_failed_recreate_leaves_no_record_so_the_next_publish_creates_it() throws Exception {
        publishedOnBandzone(originalId, "100");
        when(bzPublisher.publishNew(anyList()))
                .thenReturn(List.of(PublishResult.failed(Platform.BANDZONE, original, "wizard broke")));

        List<PlatformResult> results = service().reactivate(original);

        verify(store).remove(Platform.BANDZONE, originalId);
        verify(store, never()).record(any(), any(), any());
        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.succeeded()).isFalse();
            assertThat(r.detail()).contains("wizard broke");
        });
    }
}
