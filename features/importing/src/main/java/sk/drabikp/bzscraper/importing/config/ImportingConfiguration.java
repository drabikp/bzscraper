package sk.drabikp.bzscraper.importing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.importing.application.GigImportService;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;

import java.util.List;

@Configuration
class ImportingConfiguration {

    @Bean
    GigImportService gigImportService(List<GigImporter> importers, GigRepository gigRepository,
                                      PublishedGigStore publishedGigStore, Transactions transactions,
                                      Platforms platforms, GigWrites catalogWrites) {
        return new GigImportService(importers, gigRepository, publishedGigStore, transactions, platforms,
                catalogWrites);
    }
}
