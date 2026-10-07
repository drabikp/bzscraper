package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitSession.Created;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BandsintownGigPublisherTest {

    private final BitPortalClient portalClient = mock(BitPortalClient.class);
    private final BitSession session = mock(BitSession.class);
    private final Gig a = TestGigs.gig("A", "Venue A");
    private final Gig b = TestGigs.gig("B", "Venue B");

    @Test
    void platform_is_bandsintown() {
        assertThat(new BandsintownGigPublisher(portalClient, false).platform()).isEqualTo(Platform.BANDSINTOWN);
    }

    @Test
    void each_gig_gets_its_own_event_id_or_failure_from_one_session() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        when(session.createEvents(List.of(a, b), false))
                .thenReturn(List.of(Created.published(a, "101"), Created.failed(b, "row rejected")));

        List<PublishResult> results = new BandsintownGigPublisher(portalClient, false).publishNew(List.of(a, b));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(PublishStatus.PUBLISHED);
        assertThat(results.get(0).externalRef()).isEqualTo("101");
        assertThat(results.get(1).status()).isEqualTo(PublishStatus.FAILED);
        assertThat(results.get(1).detail()).isEqualTo("row rejected");
        verify(session).close();
    }

    @Test
    void the_notify_setting_is_passed_through() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        when(session.createEvents(anyList(), anyBoolean())).thenReturn(List.of(Created.published(a, "1")));

        new BandsintownGigPublisher(portalClient, true).publishNew(List.of(a));

        verify(session).createEvents(List.of(a), true);
    }

    @Test
    void a_failed_login_fails_the_whole_batch_with_the_reason() throws Exception {
        when(portalClient.openSession()).thenThrow(new BitUploadException("authenticator code rejected"));

        List<PublishResult> results = new BandsintownGigPublisher(portalClient, false).publishNew(List.of(a, b));

        assertThat(results).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(PublishStatus.FAILED);
            assertThat(r.detail()).isEqualTo("authenticator code rejected");
        });
    }

    @Test
    void an_empty_batch_does_not_open_the_browser() {
        assertThat(new BandsintownGigPublisher(portalClient, false).publishNew(List.of())).isEmpty();
        verifyNoInteractions(portalClient);
    }
}
