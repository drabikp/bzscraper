package sk.drabikp.bzscraper.sync.application.port.out;

/** Tells whoever shows sync state (the pages) that the outbox changed. */
public interface SyncNotifier {

    void changed();
}
