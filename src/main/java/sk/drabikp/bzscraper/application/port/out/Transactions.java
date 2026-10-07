package sk.drabikp.bzscraper.application.port.out;

/**
 * Runs several port calls as one unit: either all of their changes are stored or none.
 * Keeps transaction management out of the plain application services.
 */
public interface Transactions {

    void inTransaction(Runnable work);
}
