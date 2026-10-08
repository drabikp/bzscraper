package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sk.drabikp.bzscraper.application.port.out.Transactions;

import java.util.function.Supplier;

/** {@link Transactions} on Spring's transaction manager; repositories called inside join it. */
@Component
public class SpringTransactions implements Transactions {

    private final TransactionTemplate template;

    public SpringTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public void inTransaction(Runnable work) {
        template.executeWithoutResult(status -> work.run());
    }

    @Override
    public <T> T computeInTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
