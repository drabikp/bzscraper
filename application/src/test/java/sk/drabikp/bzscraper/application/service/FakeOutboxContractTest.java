package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncOutboxContract;

/** The service tests' in-memory outbox keeps the outbox's contract. */
class FakeOutboxContractTest extends SyncOutboxContract {

    private final SyncFakes.Outbox outbox = new SyncFakes.Outbox();

    @Override
    protected SyncOutbox outbox() {
        return outbox;
    }
}
