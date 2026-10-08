package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The 2-step "new concert" wizard: date, time and town → (maybe "similar concerts?",
 * answered "new") → name, entry, info, Facebook → Bandzone opens the created concert at
 * {@code /koncert/<id>-…}; the id is read from that address.
 */
final class BandzoneCreateWizard {

    private static final Pattern CONCERT_URL = Pattern.compile("/koncert/(\\d+)");

    private final BandzoneBrowser browser;
    private final BandzoneForm form;

    BandzoneCreateWizard(BandzoneBrowser browser) {
        this.browser = browser;
        this.form = new BandzoneForm(browser);
    }

    /** Creates the concert; returns its Bandzone id. */
    String create(Gig gig) throws BandzoneUploadException {
        browser.open("/koncert/zalozit.html?initBandPublicId=" + browser.bandSlug);
        browser.wait.until(ExpectedConditions.presenceOfElementLocated(By.name("start[date]")));
        form.setStart(gig.schedule().start());
        form.selectCity(gig.location());
        browser.clickByName("continue");
        passDuplicateScreen();
        form.fillInfo(gig);
        return send(gig);
    }

    /**
     * Between the two steps Bandzone may list similar concerts (same date and city) and ask
     * whether this is one of them; "new" continues to the info step.
     */
    private void passDuplicateScreen() {
        browser.wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.name("new")),
                ExpectedConditions.presenceOfElementLocated(By.name("name"))));
        if (!browser.driver.findElements(By.name("new")).isEmpty()) {
            browser.clickByName("new");
            browser.wait.until(ExpectedConditions.presenceOfElementLocated(By.name("name")));
        }
    }

    /**
     * Submits and reads the new concert id from the page Bandzone opens. (Searching the band
     * page by title is unreliable: same-titled gigs and the notification panel link to others.)
     */
    private String send(Gig gig) throws BandzoneUploadException {
        browser.click(browser.driver.findElement(By.name("send")));
        try {
            browser.wait.until(d -> CONCERT_URL.matcher(d.getCurrentUrl()).find());
        } catch (RuntimeException e) {
            throw new BandzoneUploadException("Gig '" + gig.title() + "' was submitted but Bandzone did not open "
                    + "the created concert (still at " + browser.driver.getCurrentUrl() + ").", e);
        }
        Matcher m = CONCERT_URL.matcher(browser.driver.getCurrentUrl());
        m.find();
        return m.group(1);
    }
}
