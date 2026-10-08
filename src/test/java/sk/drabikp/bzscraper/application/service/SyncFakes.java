package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncTrigger;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;
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
                    now, null);
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
        public void move(GigId from, GigId to, String newLabel) {
            tasks.replaceAll((id, t) -> t.gigId().equals(from) ? new SyncTask(t.id(), to, newLabel, t.platform(),
                    t.action(), t.status(), t.attempts(), t.createdAt(), t.nextAttemptAt(), t.updatedAt(),
                    t.message()) : t);
        }

        @Override
        public Optional<SyncTask> nextDue(Instant now) {
            return tasks.values().stream().filter(t -> due(t, now)).findFirst();
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
        public void markFailed(long id, String error, Instant now) {
            change(id, SyncStatus.FAILED, tasks.get(id).attempts(), null, error, now);
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
            tasks.put(id, new SyncTask(id, t.gigId(), t.gigLabel(), t.platform(), t.action(), status, attempts,
                    t.createdAt(), next, now, message));
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
            for (Platform platform : Platform.values()) {
                Publication p = records.remove(key(platform, from));
                if (p != null) {
                    record(platform, to, p.externalRef());
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
}
