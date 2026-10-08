package sk.drabikp.bzscraper.adapter.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.SyncOutboxContract;

/**
 * The JPA outbox keeps the outbox's contract (the same tests the service tests' fake passes).
 * The tests are inherited, so a class-level {@code @Transactional} wouldn't roll them back:
 * each starts from empty tables and leaves them empty instead.
 */
@SpringBootTest
class JpaOutboxContractTest extends SyncOutboxContract {

    @Autowired
    private JpaSyncOutbox outbox;
    @Autowired
    private SyncTaskJpaRepository tasks;
    @Autowired
    private SyncLogJpaRepository log;

    @BeforeEach
    @AfterEach
    void empty() {
        log.deleteAll();
        tasks.deleteAll();
    }

    @Override
    protected SyncOutbox outbox() {
        return outbox;
    }
}
