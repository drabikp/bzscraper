package sk.drabikp.bzscraper.application.service;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.TestPlatforms;
import sk.drabikp.bzscraper.application.port.out.FailureKind;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.PlatformException;
import sk.drabikp.bzscraper.domain.model.Drift;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformCheck;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.TestPlatforms.BANDZONE;

class PlatformCheckServiceTest {

    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();
    private final SyncFakes.Published published = new SyncFakes.Published();
    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();
    private final SyncFakes.Signals signals = new SyncFakes.Signals();
    private final SyncFakes.MutableClock clock = new SyncFakes.MutableClock();
    private final PlatformBreakers breakers = new PlatformBreakers(1, Duration.ofMinutes(30), clock, signals, signals);

    private final Gig fest = TestGigs.gig("Fest", "Klub 007");
    private final Gig renamedThere = TestGigs.gig("Fest — changed by hand", "Klub 007");

    private static GigImporter importer(Platform platform, Listing listed) {
        return new GigImporter() {
            @Override
            public Platform platform() {
                return platform;
            }

            @Override
            public List<ImportedGig> importGigs() throws PlatformException {
                return listed.get();
            }
        };
    }

    /** What an importer reads, or how it fails. */
    @FunctionalInterface
    private interface Listing {
        List<ImportedGig> get() throws PlatformException;
    }

    private PlatformCheckService service(GigImporter... importers) {
        return new PlatformCheckService(List.of(importers), gigs, published, outbox, new SyncFakes.DirectTransactions(),
                breakers, TestPlatforms.PLATFORMS, signals, clock, 3650);
    }

    @Test
    void reads_every_platform_and_reports_what_differs_and_what_couldnt_be_read() {
        gigs.save(fest);
        published.record(BANDZONE, fest.id(), "563380");
        published.record(BANDSINTOWN, fest.id(), "109010841");
        PlatformCheckService service = service(
                importer(BANDZONE, () -> List.of(new ImportedGig(BANDZONE, renamedThere, "563380"),
                        new ImportedGig(BANDZONE, renamedThere, "999"))),
                importer(BANDSINTOWN, () -> {
                    throw new PlatformException("Bandsintown login failed", null, FailureKind.TEMPORARY);
                }));

        PlatformCheck check = service.check().orElseThrow();

        assertThat(check.drifts()).singleElement().satisfies(drift -> {
            assertThat(drift.platform()).isEqualTo(BANDZONE);
            assertThat(drift.kind()).isEqualTo(Drift.Kind.DIFFERENT);
            assertThat(drift.differences()).singleElement().asString().startsWith("name:");
        });
        assertThat(check.unreadable()).containsEntry(BANDSINTOWN, "Bandsintown login failed");
        assertThat(check.unlinked()).containsEntry(BANDZONE, 1);
        assertThat(service.lastCheck()).contains(check);
        assertThat(service.running()).isFalse();
    }

    @Test
    void a_platform_held_back_by_its_breaker_is_not_read_and_gigs_being_synced_are_skipped() {
        gigs.save(fest);
        published.record(BANDZONE, fest.id(), "563380");
        outbox.enqueue(fest.id(), SyncTask.labelOf(fest), BANDZONE, SyncAction.UPDATE, clock.instant());
        breakers.failed(BANDSINTOWN, "timeout");
        int[] bandsintownReads = {0};
        PlatformCheckService service = service(
                importer(BANDZONE, () -> List.of(new ImportedGig(BANDZONE, renamedThere, "563380"))),
                importer(BANDSINTOWN, () -> {
                    bandsintownReads[0]++;
                    return List.of();
                }));

        PlatformCheck check = service.check().orElseThrow();

        assertThat(check.drifts()).as("its update is still queued").isEmpty();
        assertThat(check.unreadable()).containsKey(BANDSINTOWN);
        assertThat(bandsintownReads[0]).isZero();
    }

    @Test
    void history_older_than_the_window_is_not_reconciled() {
        gigs.save(fest);                                             // 2026-09-15, 23 days before "now"
        published.record(BANDZONE, fest.id(), "563380");
        PlatformCheckService lastWeekOnly = new PlatformCheckService(
                List.of(importer(BANDZONE, () -> List.of(new ImportedGig(BANDZONE, renamedThere, "563380")))),
                gigs, published, outbox, new SyncFakes.DirectTransactions(), breakers, TestPlatforms.PLATFORMS, signals,
                clock, 7);

        assertThat(lastWeekOnly.check().orElseThrow().drifts()).isEmpty();
    }

    @Test
    void forgetting_a_gone_event_drops_the_link_so_publish_creates_it_again() {
        gigs.save(fest);
        published.record(BANDZONE, fest.id(), "563380");
        PlatformCheckService service = service(importer(BANDZONE, List::of));
        assertThat(service.check().orElseThrow().drifts()).singleElement()
                .extracting(Drift::kind).isEqualTo(Drift.Kind.MISSING);

        service.forget(BANDZONE, fest.id());

        assertThat(published.isPublished(BANDZONE, fest.id())).isFalse();
        assertThat(service.lastCheck().orElseThrow().drifts()).isEmpty();
    }
}
