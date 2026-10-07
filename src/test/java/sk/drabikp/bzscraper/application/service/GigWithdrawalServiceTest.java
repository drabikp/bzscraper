package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformResult;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GigWithdrawalServiceTest {

    private final PublishedGigStore store = mock(PublishedGigStore.class);
    private final GigId id = TestGigs.gig("A", "Klub 007").id();

    private static GigWithdrawer withdrawerFor(Platform platform) {
        GigWithdrawer w = mock(GigWithdrawer.class);
        when(w.platform()).thenReturn(platform);
        return w;
    }

    @Test
    void delete_withdraws_on_the_platform_and_forgets_the_ref() throws Exception {
        when(store.externalRef(Platform.BANDZONE, id)).thenReturn(Optional.of("561859"));
        GigWithdrawer bz = withdrawerFor(Platform.BANDZONE);
        GigWithdrawalService service = new GigWithdrawalService(List.of(bz), store);

        List<PlatformResult> results = service.withdraw(id, WithdrawAction.DELETE);

        verify(bz).withdraw("561859", WithdrawAction.DELETE);
        verify(store).remove(Platform.BANDZONE, id);
        assertThat(results).singleElement().satisfies(r -> assertThat(r.succeeded()).isTrue());
    }

    @Test
    void cancel_withdraws_but_keeps_the_ref() throws Exception {
        when(store.externalRef(Platform.BANDZONE, id)).thenReturn(Optional.of("561859"));
        GigWithdrawer bz = withdrawerFor(Platform.BANDZONE);
        GigWithdrawalService service = new GigWithdrawalService(List.of(bz), store);

        service.withdraw(id, WithdrawAction.CANCEL);

        verify(bz).withdraw("561859", WithdrawAction.CANCEL);
        verify(store, never()).remove(any(), any());
    }

    @Test
    void platforms_without_a_stored_ref_are_skipped() throws Exception {
        when(store.externalRef(Platform.BANDZONE, id)).thenReturn(Optional.empty());
        GigWithdrawer bz = withdrawerFor(Platform.BANDZONE);
        GigWithdrawalService service = new GigWithdrawalService(List.of(bz), store);

        List<PlatformResult> results = service.withdraw(id, WithdrawAction.DELETE);

        verify(bz, never()).withdraw(any(), any());
        assertThat(results).isEmpty();
    }

    @Test
    void a_platform_failure_is_reported_and_the_ref_is_kept() throws Exception {
        when(store.externalRef(Platform.BANDZONE, id)).thenReturn(Optional.of("561859"));
        GigWithdrawer bz = withdrawerFor(Platform.BANDZONE);
        doThrow(new GigWithdrawalException("wizard broke")).when(bz).withdraw(any(), any());
        GigWithdrawalService service = new GigWithdrawalService(List.of(bz), store);

        List<PlatformResult> results = service.withdraw(id, WithdrawAction.DELETE);

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.succeeded()).isFalse();
            assertThat(r.detail()).isEqualTo("wizard broke");
        });
        verify(store, never()).remove(any(), any());
    }

    @Test
    void two_withdrawers_for_the_same_platform_is_rejected() {
        assertThatThrownBy(() -> new GigWithdrawalService(
                List.of(withdrawerFor(Platform.BANDZONE), withdrawerFor(Platform.BANDZONE)), store))
                .isInstanceOf(IllegalStateException.class);
    }
}
