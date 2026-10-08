package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.SyncLogEntry;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.util.List;

/** The sync log: what was, is and will be done on the platforms, and the user's say on failures. */
public interface SyncLogUseCase {

    /** Tasks not settled for good (pending, running, failed). */
    List<SyncTask> unfinished();

    List<SyncTask> recent(int limit);

    List<SyncLogEntry> log(long taskId);

    /** Runs a failed (or discarded) task again. */
    void retry(long taskId);

    /** Gives up on a pending or failed task — the platform is then left as it is. */
    void discard(long taskId);
}
