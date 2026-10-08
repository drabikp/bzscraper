package sk.drabikp.bzscraper.domain.model;

import java.util.List;

/** What a request put in the sync outbox, and what it left out and why ("already published"). */
public record QueueResult(List<SyncTask> queued, List<String> notQueued) {

    public static final QueueResult NOTHING = new QueueResult(List.of(), List.of());

    public QueueResult {
        queued = List.copyOf(queued);
        notQueued = List.copyOf(notQueued);
    }
}
