package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * A concert's delete tab: "cancel the concert" ({@code cancelGig}) or "delete it"
 * ({@code delete}). The form is submitted with the button's value — a plain click would open
 * the browser's confirm() dialog.
 */
final class BandzoneDeleteTab {

    public static final String CANCEL = "cancelGig";
    public static final String DELETE = "delete";

    private final BandzoneBrowser browser;
    private final String bandzoneId;

    public BandzoneDeleteTab(BandzoneBrowser browser, String bandzoneId) {
        this.browser = browser;
        this.bandzoneId = bandzoneId;
    }

    public void submit(String button) {
        browser.open("/koncert/" + bandzoneId + "/update?updateTabs-at=profileDeleteForm");
        WebElement pressed = browser.wait.until(ExpectedConditions.presenceOfElementLocated(By.name(button)));
        browser.js("var n=arguments[0];"
                        + "var b=document.querySelector('[name=\"'+n+'\"]'); var f=b?b.form:null;"
                        + "if(f){ if(b.value){var h=document.createElement('input');h.type='hidden';"
                        + "h.name=n;h.value=b.value;f.appendChild(h);}"
                        + " if(f.requestSubmit){f.requestSubmit(b);}else{f.submit();} }",
                button);
        browser.wait.until(ExpectedConditions.stalenessOf(pressed));      // Bandzone answered with a new page
    }
}
