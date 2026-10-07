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
import sk.drabikp.bzscraper.application.port.out.GigUpdater;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.application.service.BandsintownGigPublisher;
import sk.drabikp.bzscraper.application.service.BandzoneGigPublisher;
import sk.drabikp.bzscraper.application.service.GigCatalogService;
import sk.drabikp.bzscraper.application.service.GigCsvExportService;
import sk.drabikp.bzscraper.application.service.GigImportService;
import sk.drabikp.bzscraper.application.service.GigPublishingService;
import sk.drabikp.bzscraper.application.service.GigQueryService;
import sk.drabikp.bzscraper.application.service.GigResyncService;
import sk.drabikp.bzscraper.application.service.GigWithdrawalService;

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
    GigPublishingService gigPublishingService(List<GigPublisher> publishers, PublishedGigStore publishedGigStore) {
        return new GigPublishingService(publishers, publishedGigStore);
    }

    @Bean
    GigCatalogService gigCatalogService(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                                        Transactions transactions) {
        return new GigCatalogService(gigRepository, publishedGigStore, transactions);
    }

    @Bean
    GigImportService gigImportService(List<GigImporter> importers, GigRepository gigRepository) {
        return new GigImportService(importers, gigRepository);
    }

    @Bean
    GigWithdrawalService gigWithdrawalService(List<GigWithdrawer> withdrawers, PublishedGigStore publishedGigStore) {
        return new GigWithdrawalService(withdrawers, publishedGigStore);
    }

    @Bean
    GigResyncService gigResyncService(List<GigPublisher> publishers, List<GigUpdater> updaters,
                                      List<GigWithdrawer> withdrawers, PublishedGigStore publishedGigStore) {
        return new GigResyncService(publishers, updaters, withdrawers, publishedGigStore);
    }
}
