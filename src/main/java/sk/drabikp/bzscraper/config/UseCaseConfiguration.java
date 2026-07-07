package sk.drabikp.bzscraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigProvider;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.UploadedGigStore;
import sk.drabikp.bzscraper.application.service.BandsintownGigPublisher;
import sk.drabikp.bzscraper.application.service.BandzoneGigPublisher;
import sk.drabikp.bzscraper.application.service.GigCatalogService;
import sk.drabikp.bzscraper.application.service.GigCsvExportService;
import sk.drabikp.bzscraper.application.service.GigImportService;
import sk.drabikp.bzscraper.application.service.GigPublishingService;
import sk.drabikp.bzscraper.application.service.GigQueryService;

import java.util.List;

@Configuration
public class UseCaseConfiguration {

    @Bean
    GigQueryService gigQueryService(GigProvider gigProvider) {
        return new GigQueryService(gigProvider);
    }

    @Bean
    GigCsvExportService gigCsvExportService(GigRepository gigRepository, GigCsvExporter csvExporter) {
        return new GigCsvExportService(gigRepository, csvExporter);
    }

    @Bean
    BandsintownGigPublisher bandsintownGigPublisher(GigCsvExporter csvExporter, BitPortalClient portalClient) {
        return new BandsintownGigPublisher(csvExporter, portalClient);
    }

    @Bean
    BandzoneGigPublisher bandzoneGigPublisher(BandzonePortalClient portalClient) {
        return new BandzoneGigPublisher(portalClient);
    }

    @Bean
    GigPublishingService gigPublishingService(List<GigPublisher> publishers, UploadedGigStore uploadedGigStore) {
        return new GigPublishingService(publishers, uploadedGigStore);
    }

    @Bean
    GigCatalogService gigCatalogService(GigRepository gigRepository) {
        return new GigCatalogService(gigRepository);
    }

    @Bean
    GigImportService gigImportService(List<GigImporter> importers, GigRepository gigRepository) {
        return new GigImportService(importers, gigRepository);
    }
}
