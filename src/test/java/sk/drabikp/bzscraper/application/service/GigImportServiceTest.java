package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.ReconciliationResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GigImportServiceTest {

    private final GigRepository repository = mock(GigRepository.class);

    private static GigImporter importerFor(Platform platform, List<Gig> gigs) {
        GigImporter i = mock(GigImporter.class);
        when(i.platform()).thenReturn(platform);
        when(i.importGigs()).thenReturn(gigs);
        return i;
    }

    @Test
    void reconcile_diffs_the_platform_import_against_the_catalog() {
        Gig inCatalog = TestGigs.gig("A", "Klub 007");
        Gig onlyOnPlatform = TestGigs.gig("B", "Barrák");
        when(repository.findAll()).thenReturn(List.of(inCatalog));
        GigImporter bz = importerFor(Platform.BANDZONE, List.of(inCatalog, onlyOnPlatform));
        GigImportService service = new GigImportService(List.of(bz), repository);

        ReconciliationResult result = service.reconcile(Platform.BANDZONE);

        assertThat(result.matched()).hasSize(1);
        assertThat(result.added()).extracting(e -> e.imported().title()).containsExactly("B");
    }

    @Test
    void importable_platforms_are_the_registered_importers() {
        GigImportService service = new GigImportService(
                List.of(importerFor(Platform.BANDZONE, List.of())), repository);

        assertThat(service.importablePlatforms()).containsExactly(Platform.BANDZONE);
    }

    @Test
    void apply_saves_each_chosen_gig() {
        Gig a = TestGigs.gig("A", "Klub 007");
        Gig b = TestGigs.gig("B", "Barrák");
        GigImportService service = new GigImportService(
                List.of(importerFor(Platform.BANDZONE, List.of())), repository);

        service.apply(List.of(a, b));

        verify(repository).save(a);
        verify(repository).save(b);
    }

    @Test
    void reconciling_a_platform_with_no_importer_fails_loudly() {
        GigImportService service = new GigImportService(
                List.of(importerFor(Platform.BANDZONE, List.of())), repository);

        assertThatThrownBy(() -> service.reconcile(Platform.BANDSINTOWN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void two_importers_for_the_same_platform_is_rejected() {
        assertThatThrownBy(() -> new GigImportService(
                List.of(importerFor(Platform.BANDZONE, List.of()), importerFor(Platform.BANDZONE, List.of())),
                repository)).isInstanceOf(IllegalStateException.class);
    }
}
