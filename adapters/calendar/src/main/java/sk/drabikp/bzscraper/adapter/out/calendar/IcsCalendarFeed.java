package sk.drabikp.bzscraper.adapter.out.calendar;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.CalendarFeed;
import sk.drabikp.bzscraper.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * The band's calendar from its private iCal address ({@code bzscraper.calendar.ical-url}
 * — Google: calendar settings → "Secret address in iCal format"). The address grants read
 * access to the whole calendar, so it is configuration only and never logged or shown.
 */
@Component
public class IcsCalendarFeed implements CalendarFeed {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String address;
    private final ZoneId zone;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public IcsCalendarFeed(CalendarProperties properties) {
        this.address = properties.icalUrl().replaceFirst("^webcal://", "https://");
        this.zone = ZoneId.of(properties.zone());
    }

    @Override
    public boolean configured() {
        return !address.isEmpty();
    }

    @Override
    public List<CalendarEvent> read() throws CalendarUnavailableException {
        if (!configured()) {
            throw new CalendarUnavailableException("No calendar configured (bzscraper.calendar.ical-url)");
        }
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(address)).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CalendarUnavailableException("The calendar could not be reached: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CalendarUnavailableException("Reading the calendar was interrupted", e);
        } catch (IllegalArgumentException e) {
            throw new CalendarUnavailableException("The calendar address is not a valid URL", e);
        }
        if (response.statusCode() != 200) {
            throw new CalendarUnavailableException("The calendar answered HTTP " + response.statusCode()
                    + " — is its address still valid?");
        }
        if (!response.body().contains("BEGIN:VCALENDAR")) {
            throw new CalendarUnavailableException("The calendar address did not return an iCal calendar");
        }
        return IcsParser.parse(response.body(), zone);
    }
}
