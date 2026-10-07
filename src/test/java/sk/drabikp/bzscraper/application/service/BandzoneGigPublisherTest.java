package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BandzoneGigPublisherTest {

    private final BandzonePortalClient portalClient = mock(BandzonePortalClient.class);
    private final BandzoneSession session = mock(BandzoneSession.class);
    private final BandzoneGigPublisher publisher = new BandzoneGigPublisher(portalClient);

    private static Gig gig(String title, String venue) {
        return TestGigs.gig(title, venue);
    }

    @Test
    void platform_is_bandzone() {
        assertThat(publisher.platform()).isEqualTo(Platform.BANDZONE);
    }

    @Test
    void one_login_creates_every_gig_and_closes_the_session() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        Gig a = gig("A", "Klub 007");
        Gig b = gig("B", "Barrák");

        List<PublishResult> results = publisher.publishNew(List.of(a, b));

        assertThat(results).extracting(PublishResult::status)
                .containsExactly(PublishStatus.PUBLISHED, PublishStatus.PUBLISHED);
        verify(portalClient).openSession();
        verify(session).createGig(a);
        verify(session).createGig(b);
        verify(session).close();
    }

    @Test
    void one_gig_failing_does_not_stop_the_others_and_the_session_still_closes() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        Gig ok = gig("OK", "Klub 007");
        Gig bad = gig("Bad", "Barrák");
        doThrow(new BandzoneUploadException("city autocomplete failed")).when(session).createGig(bad);

        List<PublishResult> results = publisher.publishNew(List.of(ok, bad));

        assertThat(results).extracting(PublishResult::status)
                .containsExactly(PublishStatus.PUBLISHED, PublishStatus.FAILED);
        assertThat(results.get(1).detail()).isEqualTo("city autocomplete failed");
        verify(session).close();
    }

    @Test
    void a_login_failure_fails_the_whole_batch_and_opens_no_session() throws Exception {
        when(portalClient.openSession()).thenThrow(new BandzoneUploadException("login failed"));

        List<PublishResult> results = publisher.publishNew(List.of(gig("A", "V"), gig("B", "W")));

        assertThat(results).extracting(PublishResult::status)
                .containsExactly(PublishStatus.FAILED, PublishStatus.FAILED);
        assertThat(results).allSatisfy(r -> assertThat(r.detail()).isEqualTo("login failed"));
    }

    @Test
    void each_created_gig_is_completed_through_the_edit_form() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        Gig a = gig("A", "Klub 007");
        when(session.createGig(a)).thenReturn("561859");

        List<PublishResult> results = publisher.publishNew(List.of(a));

        verify(session).updateGig("561859", a);
        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(PublishStatus.PUBLISHED);
            assertThat(r.externalRef()).isEqualTo("561859");
            assertThat(r.detail()).isNull();
        });
    }

    @Test
    void a_gig_created_but_not_completed_is_still_published_so_it_is_not_posted_twice() throws Exception {
        when(portalClient.openSession()).thenReturn(session);
        Gig a = gig("A", "Klub 007");
        when(session.createGig(a)).thenReturn("561859");
        doThrow(new BandzoneUploadException("poster upload did not finish"))
                .when(session).updateGig("561859", a);

        List<PublishResult> results = publisher.publishNew(List.of(a));

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(PublishStatus.PUBLISHED);
            assertThat(r.externalRef()).isEqualTo("561859");
            assertThat(r.detail()).contains("poster upload did not finish");
        });
    }
}
