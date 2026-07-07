package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BandsintownGigPublisherTest {

    private final GigCsvExporter csvExporter = mock(GigCsvExporter.class);
    private final BitPortalClient portalClient = mock(BitPortalClient.class);
    private final BandsintownGigPublisher publisher = new BandsintownGigPublisher(csvExporter, portalClient);

    private static Gig gig(String title) {
        return TestGigs.gig(title, "Venue");
    }

    @Test
    void platform_is_bandsintown() {
        assertThat(publisher.platform()).isEqualTo(Platform.BANDSINTOWN);
    }

    @Test
    void successful_upload_marks_the_whole_batch_published() throws Exception {
        when(csvExporter.export(anyList())).thenReturn("csv-bytes");

        List<PublishResult> results = publisher.publishNew(List.of(gig("A"), gig("B")));

        assertThat(results).allSatisfy(r -> {
            assertThat(r.platform()).isEqualTo(Platform.BANDSINTOWN);
            assertThat(r.status()).isEqualTo(PublishStatus.PUBLISHED);
        });
        verify(portalClient).uploadCsv("csv-bytes");
    }

    @Test
    void failed_upload_fails_the_whole_batch_with_the_error_detail() throws Exception {
        when(csvExporter.export(anyList())).thenReturn("csv");
        doThrow(new BitUploadException("2FA step-up")).when(portalClient).uploadCsv(any());

        List<PublishResult> results = publisher.publishNew(List.of(gig("A"), gig("B")));

        assertThat(results).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(PublishStatus.FAILED);
            assertThat(r.detail()).isEqualTo("2FA step-up");
        });
    }
}
