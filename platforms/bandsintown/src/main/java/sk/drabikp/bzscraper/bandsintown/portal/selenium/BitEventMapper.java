package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps one event of the artist portal's event list ({@link BitEvent}) to a gig. Bandsintown has no entry fee, so
 * entry is "free" unless something better is known (see the import merge). Deleted
 * events and events without a city or date are left out.
 */
final class BitEventMapper {

    private BitEventMapper() {
    }

    public static Optional<ImportedGig> toImported(BitEvent event) {
        if (event.deleted() || event.draft() || event.venueCity() == null || event.startDate() == null
                || event.id() == null) {
            return Optional.empty();
        }
        try {
            Country country = country(event.venueCountry());
            ZoneId zone = zone(event.venueTimezone(), country);
            ZonedDateTime start = at(event.startDate(), event.startTime(), zone);
            ZonedDateTime end = event.endDate() == null ? null : at(event.endDate(), event.endTime(), zone);
            if (end != null && !end.isAfter(start)) {
                end = null;
            }
            Gig gig = new Gig(title(event),
                    new GigSchedule(start, end),
                    new Location(venue(event.venueName()), event.venueCity(), country),
                    lineup(event),
                    Admission.free(),
                    event.description(), null, null, null, false);
            boolean placed = event.latitude() != null && event.longitude() != null;
            return Optional.of(new ImportedGig(BandsintownPlatform.PLATFORM, gig, event.id(),
                    placed ? event.latitude() : null, placed ? event.longitude() : null));
        } catch (DateTimeException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String title(BitEvent event) {
        if (event.title() != null) {
            return event.title();
        }
        return event.headliner() != null ? event.headliner() : "Concert";
    }

    private static String venue(String name) {
        return name == null || name.equalsIgnoreCase("TBA") ? null : name;
    }

    /** The other bands on the bill (the headliner — the band itself — left out). */
    private static List<String> lineup(BitEvent event) {
        String headliner = event.headliner() == null ? "" : event.headliner();
        return event.lineup().stream().filter(name -> !name.equalsIgnoreCase(headliner)).toList();
    }

    private static ZonedDateTime at(String date, String time, ZoneId zone) {
        LocalTime clock = time == null ? LocalTime.MIDNIGHT : LocalTime.parse(time);
        return LocalDate.parse(date).atTime(clock).atZone(zone);
    }

    private static ZoneId zone(String timezone, Country country) {
        if (timezone != null) {
            return ZoneId.of(timezone);
        }
        return country != null ? ZoneId.of(country.timezone()) : ZoneId.of("UTC");
    }

    static Country country(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "czechia", "czech republic", "česko", "česká republika" -> Country.CZECHIA;
            case "slovakia", "slovensko" -> Country.SLOVAKIA;
            default -> null;
        };
    }
}
