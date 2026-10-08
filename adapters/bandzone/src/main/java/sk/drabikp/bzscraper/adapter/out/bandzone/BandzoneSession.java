package sk.drabikp.bzscraper.adapter.out.bandzone;

import sk.drabikp.bzscraper.domain.model.Gig;

/**
 * An authenticated Bandzone session that can create multiple gigs without logging
 * in again. Opened once per batch (see {@link BandzonePortalClient#openSession()})
 * and closed when done — turning an N-gig publish from N logins into one.
 */
public interface BandzoneSession extends AutoCloseable {

    /**
     * Creates one gig in this already-authenticated session.
     *
     * @return the Bandzone concert id of the created gig (for later update/cancel/delete)
     * @throws BandzoneUploadException if this gig's wizard fails (other gigs in the
     *                                 batch are unaffected)
     */
    String createGig(Gig gig) throws BandzoneUploadException;

    /**
     * Overwrites an existing concert's details (title, start, city, admission,
     * description, Facebook link) in place; its Bandzone id does not change. Does not
     * touch the cancelled state — Bandzone has no way to un-cancel a concert.
     */
    void updateGig(String bandzoneId, Gig gig) throws BandzoneUploadException;

    /** Marks the concert cancelled on Bandzone (it stays listed as cancelled). */
    void cancelGig(String bandzoneId) throws BandzoneUploadException;

    /** Deletes the concert from Bandzone entirely. */
    void deleteGig(String bandzoneId) throws BandzoneUploadException;

    /** Releases the session (closes the browser). Never throws. */
    @Override
    void close();
}
