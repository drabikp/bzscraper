package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GigCsvExportServiceTest {

    @Test
    void exports_all_catalog_gigs_via_the_exporter() {
        Gig a = TestGigs.gig("A", "Klub 007");
        Gig b = TestGigs.gig("B", "Barrák");
        GigRepository repository = mock(GigRepository.class);
        when(repository.findAll()).thenReturn(List.of(a, b));

        AtomicReference<List<Gig>> exported = new AtomicReference<>();
        GigCsvExporter exporter = gigs -> {
            exported.set(gigs);
            return "csv-payload";
        };

        GigCsvExportService service = new GigCsvExportService(repository, exporter);

        assertEquals("csv-payload", service.csvForCatalog());
        assertEquals(List.of(a, b), exported.get());
    }
}
