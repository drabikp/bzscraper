package sk.drabikp.bzscraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.adapter.in.sync.CheckProperties;
import sk.drabikp.bzscraper.adapter.in.sync.SyncProperties;
import sk.drabikp.bzscraper.adapter.out.calendar.CalendarProperties;
import sk.drabikp.bzscraper.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.application.port.out.GigExporter;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PlaceSearch;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SettingsStore;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.application.service.CalendarReviewService;
import sk.drabikp.bzscraper.application.service.CatalogWrites;
import sk.drabikp.bzscraper.application.service.GigCatalogService;
import sk.drabikp.bzscraper.application.service.GigExportService;
import sk.drabikp.bzscraper.application.service.GigImportService;
import sk.drabikp.bzscraper.application.service.GigPublishingService;
import sk.drabikp.bzscraper.application.service.GigResyncService;
import sk.drabikp.bzscraper.application.service.PlaceService;
import sk.drabikp.bzscraper.application.service.PlatformBreakers;
import sk.drabikp.bzscraper.application.service.PlatformCheckService;
import sk.drabikp.bzscraper.application.service.StepRegistry;
import sk.drabikp.bzscraper.application.service.SyncDispatcher;
import sk.drabikp.bzscraper.application.service.SyncLogService;
import sk.drabikp.bzscraper.application.service.SyncPause;
import sk.drabikp.bzscraper.application.service.SyncRequests;
import sk.drabikp.bzscraper.application.service.SyncWakeUp;
import sk.drabikp.bzscraper.application.service.WorkflowEngine;
import sk.drabikp.bzscraper.domain.model.BandProfile;
import sk.drabikp.bzscraper.domain.model.PlatformTraits;
import sk.drabikp.bzscraper.domain.model.Platforms;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Configuration
public class UseCaseConfiguration {

    /** The platforms, as their adapters describe them. */
    @Bean
    Platforms platforms(List<PlatformTraits> traits) {
        return new Platforms(traits);
    }

    /** Rung when platform work is queued; the sync worker listens. */
    @Bean
    SyncWakeUp syncWakeUp() {
        return new SyncWakeUp();
    }

    @Bean
    GigExportService gigExportService(GigRepository gigRepository, List<GigExporter> exporters) {
        return new GigExportService(gigRepository, exporters);
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    PlaceService placeService(PlaceSearch placeSearch) {
        return new PlaceService(placeSearch);
    }

    /** The platforms' sync steps — what each can do. */
    @Bean
    StepRegistry stepRegistry(List<SyncStep> steps, Platforms platforms, Transactions transactions,
                              PublishedGigStore publishedGigStore) {
        return new StepRegistry(steps, platforms, transactions, publishedGigStore);
    }

    /** Runs the sync tasks through the platforms' steps — docs/sync-workflow-plan.md. */
    @Bean
    WorkflowEngine workflowEngine(StepRegistry stepRegistry, GigRepository gigRepository,
                                  PublishedGigStore publishedGigStore, SyncOutbox outbox, Transactions transactions,
                                  SyncNotifier notifier, Clock clock, Platforms platforms, SyncPause syncPause,
                                  PlatformBreakers platformBreakers) {
        return new WorkflowEngine(stepRegistry, gigRepository, publishedGigStore, outbox, transactions, notifier, clock,
                platforms, syncPause, platformBreakers);
    }

    /** Reads the platforms and compares them with the catalog (reconciliation; read-only). */
    @Bean
    PlatformCheckService platformCheckService(List<GigImporter> importers, GigRepository gigRepository,
                                              PublishedGigStore publishedGigStore, SyncOutbox outbox,
                                              Transactions transactions, PlatformBreakers platformBreakers,
                                              Platforms platforms, SyncNotifier notifier, Clock clock,
                                              CheckProperties check) {
        return new PlatformCheckService(importers, gigRepository, publishedGigStore, outbox, transactions,
                platformBreakers, platforms, notifier, clock, check.pastDays());
    }

    /** A platform failing this many batches in a row is held back this long (then one trial batch). */
    @Bean
    PlatformBreakers platformBreakers(Clock clock, SyncNotifier notifier, SyncTrigger trigger, SyncProperties sync) {
        return new PlatformBreakers(sync.breaker().failures(), Duration.ofMinutes(sync.breaker().cooldownMinutes()),
                clock, notifier, trigger);
    }

    @Bean
    SyncRequests syncRequests(SyncOutbox outbox, PublishedGigStore publishedGigStore, SyncTrigger trigger,
                              SyncNotifier notifier, Clock clock, StepRegistry stepRegistry, Platforms platforms) {
        return new SyncRequests(outbox, publishedGigStore, trigger, notifier, clock, stepRegistry, platforms);
    }

    @Bean
    GigPublishingService gigPublishingService(SyncRequests syncRequests, Transactions transactions,
                                              Platforms platforms) {
        return new GigPublishingService(syncRequests, transactions, platforms);
    }

    @Bean
    CatalogWrites catalogWrites(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                                CalendarLinkStore calendarLinks, SyncRequests syncRequests) {
        return new CatalogWrites(gigRepository, publishedGigStore, calendarLinks, syncRequests);
    }

    @Bean
    GigCatalogService gigCatalogService(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                                        Transactions transactions, SyncRequests syncRequests,
                                        CatalogWrites catalogWrites) {
        return new GigCatalogService(gigRepository, publishedGigStore, transactions, syncRequests, catalogWrites);
    }

    @Bean
    GigImportService gigImportService(List<GigImporter> importers, GigRepository gigRepository,
                                      PublishedGigStore publishedGigStore, Transactions transactions,
                                      Platforms platforms, CatalogWrites catalogWrites) {
        return new GigImportService(importers, gigRepository, publishedGigStore, transactions, platforms,
                catalogWrites);
    }

    @Bean
    GigResyncService gigResyncService(GigRepository gigRepository, SyncRequests syncRequests,
                                      Transactions transactions) {
        return new GigResyncService(gigRepository, syncRequests, transactions);
    }

    @Bean
    SyncPause syncPause(SyncTrigger trigger, SyncNotifier notifier, SettingsStore settings) {
        return new SyncPause(trigger, notifier, settings);
    }

    @Bean
    SyncDispatcher syncDispatcher(WorkflowEngine workflowEngine, SyncOutbox outbox, SyncNotifier notifier,
                                  Clock clock, SyncPause syncPause, PlatformBreakers platformBreakers,
                                  Platforms platforms) {
        return new SyncDispatcher(workflowEngine, outbox, notifier, clock, syncPause, platformBreakers, platforms);
    }

    @Bean
    SyncLogService syncLogService(SyncOutbox outbox, SyncTrigger trigger, SyncNotifier notifier, Clock clock) {
        return new SyncLogService(outbox, trigger, notifier, clock);
    }

    @Bean
    CalendarReviewService calendarReviewService(
            CalendarFeed feed, BandProfileStore profileStore, CalendarDecisionStore decisionStore,
            CalendarSnapshotStore snapshotStore, CalendarLinkStore linkStore, GigRepository gigRepository,
            Transactions transactions, Clock clock, CatalogWrites catalogWrites, CalendarProperties calendar) {
        return new CalendarReviewService(feed, profileStore, decisionStore, snapshotStore, linkStore, gigRepository,
                transactions, clock, new BandProfile.Thresholds(calendar.gigScore(), calendar.notGigScore(),
                        calendar.strongNegative()), catalogWrites);
    }
}
