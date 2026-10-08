package sk.drabikp.bzscraper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigProvider;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PlaceSearch;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.application.service.CalendarReviewService;
import sk.drabikp.bzscraper.application.service.GigCatalogService;
import sk.drabikp.bzscraper.application.service.GigCsvExportService;
import sk.drabikp.bzscraper.application.service.GigImportService;
import sk.drabikp.bzscraper.application.service.GigPublishingService;
import sk.drabikp.bzscraper.application.service.GigQueryService;
import sk.drabikp.bzscraper.application.service.GigResyncService;
import sk.drabikp.bzscraper.application.service.PlaceService;
import sk.drabikp.bzscraper.application.service.SyncDispatcher;
import sk.drabikp.bzscraper.application.service.SyncLogService;
import sk.drabikp.bzscraper.application.service.SyncPause;
import sk.drabikp.bzscraper.application.service.SyncRequests;
import sk.drabikp.bzscraper.application.service.WorkflowEngine;
import sk.drabikp.bzscraper.domain.model.BandProfile;

import java.time.Clock;
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
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    PlaceService placeService(PlaceSearch placeSearch) {
        return new PlaceService(placeSearch);
    }

    /** Runs the workflow actions (update) through the platforms' steps — docs/sync-workflow-plan.md. */
    @Bean
    WorkflowEngine workflowEngine(List<SyncStep> steps, GigRepository gigRepository,
                                  PublishedGigStore publishedGigStore, SyncOutbox outbox, Transactions transactions,
                                  SyncNotifier notifier, Clock clock, SyncPause syncPause) {
        return new WorkflowEngine(steps, gigRepository, publishedGigStore, outbox, transactions, notifier, clock,
                syncPause::paused);
    }

    @Bean
    SyncRequests syncRequests(SyncOutbox outbox, PublishedGigStore publishedGigStore, SyncTrigger trigger,
                              SyncNotifier notifier, Clock clock, WorkflowEngine workflowEngine) {
        return new SyncRequests(outbox, publishedGigStore, trigger, notifier, clock, workflowEngine);
    }

    @Bean
    GigPublishingService gigPublishingService(SyncRequests syncRequests, Transactions transactions) {
        return new GigPublishingService(syncRequests, transactions);
    }

    @Bean
    GigCatalogService gigCatalogService(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                                        CalendarLinkStore calendarLinks, Transactions transactions,
                                        SyncRequests syncRequests) {
        return new GigCatalogService(gigRepository, publishedGigStore, calendarLinks, transactions, syncRequests);
    }

    @Bean
    GigImportService gigImportService(List<GigImporter> importers, GigRepository gigRepository,
                                      PublishedGigStore publishedGigStore, Transactions transactions) {
        return new GigImportService(importers, gigRepository, publishedGigStore, transactions);
    }

    @Bean
    GigResyncService gigResyncService(GigRepository gigRepository, SyncRequests syncRequests,
                                      Transactions transactions) {
        return new GigResyncService(gigRepository, syncRequests, transactions);
    }

    @Bean
    SyncPause syncPause(SyncTrigger trigger, SyncNotifier notifier) {
        return new SyncPause(trigger, notifier);
    }

    @Bean
    SyncDispatcher syncDispatcher(WorkflowEngine workflowEngine, SyncOutbox outbox, SyncNotifier notifier,
                                  Clock clock, SyncPause syncPause) {
        return new SyncDispatcher(workflowEngine, outbox, notifier, clock, syncPause);
    }

    @Bean
    SyncLogService syncLogService(SyncOutbox outbox, SyncTrigger trigger, SyncNotifier notifier, Clock clock) {
        return new SyncLogService(outbox, trigger, notifier, clock);
    }

    @Bean
    CalendarReviewService calendarReviewService(
            CalendarFeed feed, BandProfileStore profileStore, CalendarDecisionStore decisionStore,
            CalendarSnapshotStore snapshotStore, CalendarLinkStore linkStore, GigRepository gigRepository,
            Transactions transactions, Clock clock,
            @Value("${bzscraper.calendar.gig-score:4}") int gigScore,
            @Value("${bzscraper.calendar.not-gig-score:-1}") int notGigScore,
            @Value("${bzscraper.calendar.strong-negative:-4}") int strongNegative) {
        return new CalendarReviewService(feed, profileStore, decisionStore, snapshotStore, linkStore, gigRepository,
                transactions, clock, new BandProfile.Thresholds(gigScore, notGigScore, strongNegative));
    }
}
