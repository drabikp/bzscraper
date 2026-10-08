package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.adapter.out.browser.PlatformBrowser;
import sk.drabikp.bzscraper.domain.model.Gig;

/**
 * One logged-in Bandzone browser, working through the band admin's pages — each a page object
 * ({@link BandzoneCreateWizard}, {@link BandzoneUpdateForm}, {@link BandzoneDeleteTab},
 * {@link BandzoneLineupPage}). Anything unexpected from the browser fails only that gig, as a
 * temporary failure; the session stays open for the rest.
 */
final class SeleniumBandzoneSession implements BandzoneSession {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumBandzoneSession.class);

    private final PlatformBrowser.Lease lease;
    private final BandzoneBrowser browser;
    private String ownBandName; // read once per session from the band's profile

    SeleniumBandzoneSession(PlatformBrowser.Lease lease, WebDriverWait wait, String baseUrl, String bandSlug) {
        this.lease = lease;
        this.browser = new BandzoneBrowser(lease.driver(), wait, baseUrl, bandSlug);
    }

    @Override
    public String createGig(Gig gig) throws BandzoneUploadException {
        return guarded("create", "Bandzone create failed for '" + gig.title() + "'", () -> {
            String id = new BandzoneCreateWizard(browser).create(gig);
            logger.info("Bandzone gig created: '{}' (id {})", gig.title(), id);
            return id;
        });
    }

    @Override
    public void updateGig(String bandzoneId, Gig gig) throws BandzoneUploadException {
        guarded("update", "Bandzone update failed for concert " + bandzoneId, () -> {
            new BandzoneUpdateForm(browser, bandzoneId).save(gig);
            new BandzoneLineupPage(browser, bandzoneId).sync(gig.lineup(), ownBandName());
            logger.info("Bandzone gig updated: '{}' (id {})", gig.title(), bandzoneId);
            return null;
        });
    }

    @Override
    public void cancelGig(String bandzoneId) throws BandzoneUploadException {
        guarded("cancel", "Bandzone cancel failed for concert " + bandzoneId, () -> {
            new BandzoneDeleteTab(browser, bandzoneId).submit(BandzoneDeleteTab.CANCEL);
            return null;
        });
    }

    @Override
    public void deleteGig(String bandzoneId) throws BandzoneUploadException {
        guarded("delete", "Bandzone delete failed for concert " + bandzoneId, () -> {
            new BandzoneDeleteTab(browser, bandzoneId).submit(BandzoneDeleteTab.DELETE);
            return null;
        });
    }

    /** Hands the browser back (kept open a little for the next session). */
    @Override
    public void close() {
        lease.keepWarm();
    }

    /**
     * The publishing band's display name, as it appears in a concert's performer list. Read
     * from {@code og:title}: the profile's {@code <h1>} also holds genre and city.
     */
    private String ownBandName() throws BandzoneUploadException {
        if (ownBandName == null) {
            browser.open("/" + browser.bandSlug);
            String name = browser.wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("meta[property='og:title']"))).getDomAttribute("content");
            if (name == null || name.isBlank()) {
                throw new BandzoneUploadException("Could not read the band name of '" + browser.bandSlug + "'.");
            }
            ownBandName = name.trim();
        }
        return ownBandName;
    }

    /** One operation: a failure leaves a screenshot; a browser error fails it as temporary. */
    private <T> T guarded(String step, String failure, PlatformBrowser.Work<T, BandzoneUploadException> work)
            throws BandzoneUploadException {
        return lease.guarded(step, work, e -> new BandzoneUploadException(failure + ": " + e.getMessage(), e));
    }
}
