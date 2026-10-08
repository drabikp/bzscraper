package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Bandsintown's "Are you sure you want to remove this event?" dialog, opened from a list
 * row's menu or the event's form: a reason, a detail, then the dialog's own Delete.
 * Everything is looked up INSIDE the dialog — the form behind it has dropdowns, text areas
 * and a Delete button of its own.
 */
final class BitDeleteDialog {

    private final BitPortal portal;

    BitDeleteDialog(BitPortal portal) {
        this.portal = portal;
    }

    /** Removes the event: reason "canceled" for a cancelled gig, else "other". */
    void confirm(String eventId, RemovalReason reason) throws BitUploadException {
        portal.wait.until(ExpectedConditions.presenceOfElementLocated(By.name("reason")));
        portal.pause();
        portal.click(dialog().findElement(By.cssSelector("button[aria-label='Toggle dropdown']")));
        portal.click(portal.wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("li[value='" + reason.name() + "'] button"))));
        if (portal.driver.findElement(By.name("reason")).getDomProperty("value").isBlank()) {
            throw new BitUploadException("Could not pick a reason in Bandsintown's delete dialog — nothing was deleted.");
        }
        portal.pause();
        portal.type(dialog().findElement(By.tagName("textarea")),
                reason.comment());

        int mark = portal.replies.count();
        portal.pause();
        portal.click(dialog().findElement(By.xpath(".//button[normalize-space()='Delete']")));
        PortalReply reply = portal.replies.await(mark, r -> r.write() && r.urlContains("/events/" + eventId),
                "the delete");
        if (!reply.event().map(BitEvent::deleted).orElse(false)) {
            throw new BitUploadException("Bandsintown did not delete event " + eventId + " — " + reply.describe());
        }
        portal.pause();
    }

    /** The nearest box around the reason field that holds a Delete button (found anew — it re-renders). */
    private WebElement dialog() {
        return portal.driver.findElement(By.xpath(
                "//input[@name='reason']/ancestor::*[.//button[normalize-space()='Delete']][1]"));
    }
}
