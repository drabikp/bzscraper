package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A concert's edit form ({@code /koncert/<id>/update}): poster, town, club, dates, info —
 * saved, then read back, since a rejected form re-renders with the submitted values.
 * Autocomplete picks and the poster upload re-render the form via AJAX, so they go first.
 */
final class BandzoneUpdateForm {

    private static final Duration UPLOAD_WAIT = Duration.ofSeconds(60);

    private final BandzoneBrowser browser;
    private final BandzoneForm form;
    private final String bandzoneId;

    BandzoneUpdateForm(BandzoneBrowser browser, String bandzoneId) {
        this.browser = browser;
        this.form = new BandzoneForm(browser);
        this.bandzoneId = bandzoneId;
    }

    /** Opens the form, sets every field from the gig, saves and checks what Bandzone stored. */
    void save(Gig gig) throws BandzoneUploadException {
        open();
        if (gig.posterImageUrl() != null) {
            uploadPoster(gig.posterImageUrl());
        }
        form.selectCity(gig.location());
        form.selectVenue(gig.location().venue());
        form.setStart(gig.schedule().start());
        form.setEnd(gig.schedule().hasEnd() ? gig.schedule().end() : null);
        form.fillInfo(gig);
        WebElement send = browser.driver.findElement(By.id("frmupdateForm-send"));
        browser.click(send);
        browser.wait.until(ExpectedConditions.stalenessOf(send));
        verifyStored(gig);
    }

    private void open() {
        browser.open("/koncert/" + bandzoneId + "/update?updateTabs-at=updateForm");
        browser.wait.until(ExpectedConditions.presenceOfElementLocated(By.id("frmupdateForm-send")));
    }

    /** Re-reads the stored concert and compares the fields that identify the gig. */
    private void verifyStored(Gig gig) throws BandzoneUploadException {
        open();
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("name", gig.title());
        expected.put("start[date]", gig.schedule().start().format(BandzoneForm.DATE));
        expected.put("start[time]", gig.schedule().start().format(BandzoneForm.TIME));
        expected.put("end[date]", gig.schedule().hasEnd() ? gig.schedule().end().format(BandzoneForm.DATE) : "");
        expected.put("cityId__container[textInput]", gig.location().city());
        expected.put("venueId__container[textInput]", gig.location().venue() == null ? "" : gig.location().venue());
        expected.put("entry", gig.admission().isPaid() ? gig.admission().amount() : "");
        expected.put("info", gig.description() == null ? "" : gig.description());
        List<String> mismatched = new ArrayList<>();
        expected.forEach((field, value) -> {
            String stored = browser.driver.findElement(By.name(field)).getDomProperty("value");
            if (!value.equalsIgnoreCase(stored == null ? "" : stored.trim())) {
                mismatched.add(field + "='" + stored + "' (expected '" + value + "')");
            }
        });
        String storedType = (String) browser.js("var r=document.querySelector('[name=entryType]:checked');return r?r.value:'';");
        String expectedType = BandzoneForm.entryTypeValue(gig.admission().type());
        if (!expectedType.equals(storedType)) {
            mismatched.add("entryType='" + storedType + "' (expected '" + expectedType + "')");
        }
        if (!mismatched.isEmpty()) {
            throw new BandzoneUploadException("Bandzone did not save the changes to concert " + bandzoneId + ": "
                    + String.join(", ", mismatched));
        }
    }

    /**
     * Downloads the poster and uploads it through the form's auto-uploading file input
     * (Bandzone keeps one image; a new upload replaces it). Bandzone offers no way to remove a
     * poster, so a gig without one leaves the current image alone.
     */
    private void uploadPoster(String posterUrl) throws BandzoneUploadException {
        Path file = downloadPoster(posterUrl);
        try {
            By input = By.name("profileImage[uploader]");
            browser.wait.until(d -> d.findElement(input).isEnabled()); // enabled once the uploader's JS is ready
            WebElement formBefore = browser.driver.findElement(By.id("frmupdateForm-send"));
            browser.driver.findElement(input).sendKeys(file.toAbsolutePath().toString());
            new WebDriverWait(browser.driver, UPLOAD_WAIT).until(d -> (Boolean) browser.js(
                    "var c=document.querySelector('[id^=files-container]');"
                            + "return !!c && /Nahráno/.test(c.innerText) && !c.querySelector('.template-upload');"));
            browser.awaitReload(formBefore);       // the uploader reloads the form when done
            browser.wait.until(ExpectedConditions.presenceOfElementLocated(By.id("frmupdateForm-send")));
        } catch (TimeoutException e) {
            throw new BandzoneUploadException("Poster upload did not finish: " + posterUrl, e);
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // temp file; the OS cleans it up eventually
            }
        }
    }

    private static Path downloadPoster(String posterUrl) throws BandzoneUploadException {
        try {
            String path = URI.create(posterUrl).getPath();
            String ext = path != null && path.matches(".*\\.(?i)(jpe?g|png|gif|webp)$")
                    ? path.substring(path.lastIndexOf('.')) : ".jpg";
            Path file = Files.createTempFile("bz-poster-", ext);
            HttpResponse<Path> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL).build()
                    .send(HttpRequest.newBuilder(URI.create(posterUrl)).GET().build(),
                            HttpResponse.BodyHandlers.ofFile(file));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(file);
                throw new BandzoneUploadException("Poster download failed (HTTP " + response.statusCode() + "): "
                        + posterUrl);
            }
            return file;
        } catch (IOException | IllegalArgumentException e) {
            throw new BandzoneUploadException("Poster download failed: " + posterUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BandzoneUploadException("Poster download interrupted: " + posterUrl, e);
        }
    }
}
