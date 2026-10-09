package sk.drabikp.bzscraper.sync.application.port.in;

import sk.drabikp.bzscraper.sync.domain.SyncCounts;
import sk.drabikp.bzscraper.sync.domain.SyncLogEntry;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;

/** The sync log: what was, is and will be done on the platforms, and the user's say on failures. */
public interface SyncLogUseCase {

    /** Tasks not settled for good (pending, running, failed). */
    List<SyncTask> unfinished();

    List<SyncTask> recent(int limit);

    /** Which tasks a list shows: the latest ones, those not finished (failed included), or those waiting for the user. */
    enum Show { ALL, OPEN, FAILED }

    /** The tasks to list, newest first. */
    List<SyncTask> tasks(Show show);

    /** The unfinished work (failed included) by what it is doing now. */
    SyncCounts counts();

    List<SyncLogEntry> log(long taskId);

    /** Runs a failed (or discarded) task again. */
    void retry(long taskId);

    /** Gives up on a pending or failed task — the platform is then left as it is. */
    void discard(long taskId);
}
