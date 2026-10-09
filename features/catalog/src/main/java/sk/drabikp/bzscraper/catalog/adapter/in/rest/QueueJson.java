package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import sk.drabikp.bzscraper.sync.domain.QueueResult;

import java.util.List;

/** What a change put in the sync outbox, and what it left out (with why, in English). */
record QueueJson(List<Queued> queued, List<String> notQueued) {

    record Queued(long taskId, String platform, String action) {
    }

    static QueueJson of(QueueResult result) {
        return new QueueJson(result.queued().stream()
                .map(t -> new Queued(t.id(), t.platform().id(), t.action().name())).toList(), result.notQueued());
    }
}
