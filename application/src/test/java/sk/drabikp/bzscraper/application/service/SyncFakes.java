package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.TestPlatforms;
import sk.drabikp.bzscraper.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.SettingsStore;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.StepType;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncLogEntry;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** In-memory stand-ins for the sync ports, behaving like the JPA adapters. */
final class SyncFakes {

    private SyncFakes() {
    }

    static final class Outbox implements SyncOutbox {

        private final Map<Long, SyncTask> tasks = new LinkedHashMap<>();
        private final List<SyncLogEntry> log = new ArrayList<>();
        private long nextId = 1;

        List<SyncTask> all() {
            return List.copyOf(tasks.values());
        }

        SyncTask get(long id) {
            return tasks.get(id);
        }

        @Override
        public SyncTask enqueue(GigId gigId, String gigLabel, Platform platform, SyncAction action, Instant now) {
            SyncTask task = new SyncTask(nextId++, gigId, gigLabel, platform, action, SyncStatus.PENDING, 0, now, now,
                    now, null, null);
            tasks.put(task.id(), task);
            write(task.id(), now, "queued");
            return task;
        }

        @Override
        public Optional<SyncTask> find(long id) {
            return Optional.ofNullable(tasks.get(id));
        }

        @Override
        public List<SyncTask> openFor(GigId gigId) {
            return tasks.values().stream().filter(t -> t.gigId().equals(gigId) && t.status().open()).toList();
        }

        @Override
        public List<SyncTask> unfinished() {
            return tasks.values().stream().filter(t -> t.status().open() || t.status() == SyncStatus.FAILED).toList();
        }

        @Override
        public List<SyncTask> recent(int limit) {
            return tasks.values().stream().sorted(Comparator.comparingLong(SyncTask::id).reversed())
                    .limit(limit).toList();
        }

        @Override
        public List<SyncLogEntry> log(long taskId) {
            return log.stream().filter(e -> e.taskId() == taskId).toList();
        }

        @Override
        public List<SyncTask> supersede(GigId gigId, Platform platform, String why, Instant now) {
            List<SyncTask> pending = tasks.values().stream().filter(t -> t.gigId().equals(gigId)
                    && t.platform() == platform && t.status() == SyncStatus.PENDING).toList();
            pending.forEach(t -> change(t.id(), SyncStatus.DISCARDED, t.attempts(), null, "superseded: " + why, now));
            return pending;
        }

        @Override
        public List<SyncTask> replaceFailed(GigId gigId, Platform platform, Set<SyncAction> actions, String why,
                                            Instant now) {
            List<SyncTask> failed = tasks.values().stream().filter(t -> t.gigId().equals(gigId)
                    && t.platform() == platform && t.status() == SyncStatus.FAILED && actions.contains(t.action()))
                    .toList();
            failed.forEach(t -> change(t.id(), SyncStatus.DISCARDED, t.attempts(), null, "replaced: " + why, now));
            return failed;
        }

        @Override
        public void move(GigId from, GigId to, String newLabel) {
            tasks.replaceAll((id, t) -> t.gigId().equals(from) ? new SyncTask(t.id(), to, newLabel, t.platform(),
                    t.action(), t.status(), t.attempts(), t.createdAt(), t.nextAttemptAt(), t.updatedAt(),
                    t.message(), t.step()) : t);
        }

        @Override
        public Optional<SyncTask> nextDue(Instant now) {
            return tasks.values().stream().filter(t -> due(t, now)).findFirst();
        }

        @Override
        public List<SyncTask> due(Instant now) {
            return tasks.values().stream().filter(t -> due(t, now)).toList();
        }

        @Override
        public List<SyncTask> due(Platform platform, SyncAction action, Instant now) {
            return tasks.values().stream()
                    .filter(t -> due(t, now) && t.platform() == platform && t.action() == action).toList();
        }

        @Override
        public List<SyncTask> running() {
            return tasks.values().stream().filter(t -> t.status() == SyncStatus.RUNNING).toList();
        }

        @Override
        public boolean markRunning(long id, Instant now) {
            SyncTask t = tasks.get(id);
            if (t.status() != SyncStatus.PENDING) {
                return false;
            }
            change(id, SyncStatus.RUNNING, t.attempts() + 1, null, t.message(), now);
            return true;
        }

        @Override
        public void markDone(long id, String message, Instant now) {
            change(id, SyncStatus.DONE, tasks.get(id).attempts(), null, message, now);
        }

        @Override
        public void markRetry(long id, String error, Instant nextAttempt, Instant now) {
            change(id, SyncStatus.PENDING, tasks.get(id).attempts(), nextAttempt, error, now);
        }

        @Override
        public void postpone(long id, String why, Instant nextAttempt, Instant now) {
            SyncTask t = tasks.get(id);
            if (t.status() == SyncStatus.RUNNING) {
                change(id, SyncStatus.PENDING, Math.max(0, t.attempts() - 1), nextAttempt, why, now);
            }
        }

        @Override
        public void markFailed(long id, String error, Instant now) {
            change(id, SyncStatus.FAILED, tasks.get(id).attempts(), null, error, now);
        }

        @Override
        public void advance(long id, StepType next, String why, Instant now) {
            SyncTask t = tasks.get(id);
            if (!t.status().canBecome(SyncStatus.PENDING)) {
                return;                                 // as the real outbox: a finished task stays so
            }
            tasks.put(id, new SyncTask(id, t.gigId(), t.gigLabel(), t.platform(), t.action(), SyncStatus.PENDING, 0,
                    t.createdAt(), now, now, t.message(), next));
            write(id, now, why + " → " + next.label());
        }

        @Override
        public void requeue(long id, Instant now) {
            change(id, SyncStatus.PENDING, 0, now, null, now);
        }

        @Override
        public void discard(long id, Instant now) {
            change(id, SyncStatus.DISCARDED, tasks.get(id).attempts(), null, "discarded by you", now);
        }

        private static boolean due(SyncTask t, Instant now) {
            return t.status() == SyncStatus.PENDING && !t.nextAttemptAt().isAfter(now);
        }

        private void change(long id, SyncStatus status, int attempts, Instant next, String message, Instant now) {
            SyncTask t = tasks.get(id);
            if (!t.status().canBecome(status)) {
                return;                                 // as the real outbox: a late or racing write is ignored
            }
            tasks.put(id, new SyncTask(id, t.gigId(), t.gigLabel(), t.platform(), t.action(), status, attempts,
                    t.createdAt(), next, now, message, t.step()));
            write(id, now, status + (message == null ? "" : ": " + message));
        }

        private void write(long id, Instant at, String message) {
            log.add(new SyncLogEntry(id, at, message));
        }
    }

    static final class Published implements PublishedGigStore {

        private final Map<String, Publication> records = new LinkedHashMap<>();

        private static String key(Platform platform, GigId id) {
            return platform + "|" + id;
        }

        @Override
        public boolean isPublished(Platform platform, GigId gigId) {
            return records.containsKey(key(platform, gigId));
        }

        @Override
        public Optional<String> externalRef(Platform platform, GigId gigId) {
            return Optional.ofNullable(records.get(key(platform, gigId))).map(Publication::externalRef)
                    .filter(ref -> !ref.isBlank());
        }

        @Override
        public void record(Platform platform, GigId gigId, String externalRef) {
            records.put(key(platform, gigId), new Publication(platform, gigId, externalRef));
        }

        @Override
        public void remove(Platform platform, GigId gigId) {
            records.remove(key(platform, gigId));
        }

        @Override
        public void move(GigId from, GigId to) {
            for (Publication p : List.copyOf(records.values())) {
                if (p.gigId().equals(from)) {
                    records.remove(key(p.platform(), from));
                    record(p.platform(), to, p.externalRef());
                }
            }
        }

        @Override
        public List<Publication> all() {
            return List.copyOf(records.values());
        }
    }

    static final class Gigs implements GigRepository {

        private final Map<GigId, Gig> gigs = new HashMap<>();

        @Override
        public void save(Gig gig) {
            gigs.put(gig.id(), gig);
        }

        @Override
        public Optional<Gig> findById(GigId id) {
            return Optional.ofNullable(gigs.get(id));
        }

        @Override
        public List<Gig> findAll() {
            return List.copyOf(gigs.values());
        }

        @Override
        public List<Gig> findStartingWithin(DateRange range) {
            return findAll();
        }

        @Override
        public void deleteById(GigId id) {
            gigs.remove(id);
        }
    }

    /** Runs the work directly; counts how often a transaction was used. */
    static final class DirectTransactions implements Transactions {

        int used;

        @Override
        public void inTransaction(Runnable work) {
            used++;
            work.run();
        }

        @Override
        public <T> T computeInTransaction(Supplier<T> work) {
            used++;
            return work.get();
        }
    }

    static final class Signals implements SyncTrigger, SyncNotifier {

        int wakes;
        int changes;

        @Override
        public void wake() {
            wakes++;
        }

        @Override
        public void changed() {
            changes++;
        }
    }

    /** Settings in memory. */
    static final class Settings implements SettingsStore {

        final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> get(String name) {
            return Optional.ofNullable(values.get(name));
        }

        @Override
        public void put(String name, String value) {
            if (value == null) {
                values.remove(name);
            } else {
                values.put(name, value);
            }
        }
    }

    /** A clock the test moves forward. */
    static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-08T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A workflow step that records what it was given; refusal and outcome are set per test. */
    static final class Step implements SyncStep {

        private final Platform platform;
        private final StepType type;
        private final int batchSize;
        Function<Gig, Optional<String>> refusal = gig -> Optional.empty();
        Function<Item, StepOutcome> outcome = item -> StepOutcome.done(null);
        final List<List<Item>> calls = new ArrayList<>();

        Step(Platform platform, StepType type, int batchSize) {
            this.platform = platform;
            this.type = type;
            this.batchSize = batchSize;
        }

        @Override
        public Platform platform() {
            return platform;
        }

        @Override
        public StepType type() {
            return type;
        }

        @Override
        public int batchSize() {
            return batchSize;
        }

        @Override
        public Optional<String> refusal(Gig gig) {
            return refusal.apply(gig);
        }

        @Override
        public List<StepOutcome> run(List<Item> items) {
            calls.add(List.copyOf(items));
            return items.stream().map(outcome).toList();
        }

        List<Item> ran() {
            return calls.stream().flatMap(List::stream).toList();
        }
    }

    // --- the sync services wired with these fakes and TestPlatforms ---

    static StepRegistry registry(Published published, SyncStep... steps) {
        return new StepRegistry(List.of(steps), TestPlatforms.PLATFORMS, new DirectTransactions(), published);
    }

    static WorkflowEngine engine(StepRegistry steps, Gigs gigs, Published published, Outbox outbox, Signals signals,
                                 Clock clock, PauseSyncUseCase pause, PlatformHealth health) {
        return new WorkflowEngine(steps, gigs, published, outbox, new DirectTransactions(), signals, clock,
                TestPlatforms.PLATFORMS, pause, health);
    }

    /** Never paused, platforms never held back. */
    static WorkflowEngine engine(StepRegistry steps, Gigs gigs, Published published, Outbox outbox, Signals signals,
                                 Clock clock) {
        return engine(steps, gigs, published, outbox, signals, clock, new SyncPause(signals, signals, new Settings()),
                PlatformBreakers.never(clock));
    }

    static SyncDispatcher dispatcher(WorkflowEngine engine, Outbox outbox, Signals signals, Clock clock,
                                     PauseSyncUseCase pause, PlatformHealth health) {
        return new SyncDispatcher(engine, outbox, signals, clock, pause, health, TestPlatforms.PLATFORMS);
    }

    static SyncDispatcher dispatcher(WorkflowEngine engine, Outbox outbox, Signals signals, Clock clock) {
        return dispatcher(engine, outbox, signals, clock, new SyncPause(signals, signals, new Settings()), PlatformBreakers.never(clock));
    }

    static CatalogWrites writes(Gigs gigs, Published published, CalendarLinkStore links, SyncRequests requests) {
        return new CatalogWrites(gigs, published, links, requests);
    }

    static GigCatalogService catalog(Gigs gigs, Published published, CalendarLinkStore links, SyncRequests requests) {
        return catalog(gigs, published, links, new DirectTransactions(), requests);
    }

    static GigCatalogService catalog(Gigs gigs, Published published, CalendarLinkStore links,
                                     DirectTransactions transactions, SyncRequests requests) {
        return new GigCatalogService(gigs, published, transactions, requests,
                writes(gigs, published, links, requests));
    }

    /** Every platform admits all work (tests that don't care which steps the platforms have). */
    static SyncRequests requests(Outbox outbox, Published published, Signals signals, Clock clock) {
        return requests(outbox, published, signals, clock, SyncAdmission.ALL);
    }

    static SyncRequests requests(Outbox outbox, Published published, Signals signals, Clock clock,
                                 SyncAdmission admission) {
        return new SyncRequests(outbox, published, signals, signals, clock, admission, TestPlatforms.PLATFORMS);
    }
}
