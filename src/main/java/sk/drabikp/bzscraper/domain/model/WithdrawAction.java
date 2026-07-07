package sk.drabikp.bzscraper.domain.model;

/** How to withdraw a published gig from a platform. */
public enum WithdrawAction {
    /** Mark the gig cancelled on the platform (it stays listed as cancelled). */
    CANCEL,
    /** Remove the gig from the platform entirely. */
    DELETE
}
