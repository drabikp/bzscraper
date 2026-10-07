package sk.drabikp.bzscraper.adapter.out.bandsintown;

import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Location;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps one event of the artist portal's event list (as captured live:
 * {@code id, status, title, use_custom_title, headline_artist_name, venue_name,
 * venue_city, venue_country, venue_timezone, start_date, start_time, end_date,
 * end_time, description, lineup, free}) to a gig. Bandsintown has no entry fee, so
 * entry is "free" unless something better is known (see the import merge). Deleted
 * events and events without a city or date are left out.
 */
final class BitEventMapper {

    private BitEventMapper() {
    }

    static Optional<ImportedGig> toImported(Map<String, Object> event) {
        String status = text(event.get("status"));
        String city = text(event.get("venue_city"));
        String startDate = text(event.get("start_date"));
        Object id = event.get("id");
        if ("DELETED".equals(status) || "DRAFT".equals(status) || city == null || startDate == null || id == null) {
            return Optional.empty();
        }
        try {
            Country country = country(text(event.get("venue_country")));
            ZoneId zone = zone(text(event.get("venue_timezone")), country);
            ZonedDateTime start = at(startDate, text(event.get("start_time")), zone);
            ZonedDateTime end = text(event.get("end_date")) == null ? null
                    : at(text(event.get("end_date")), text(event.get("end_time")), zone);
            if (end != null && !end.isAfter(start)) {
                end = null;
            }
            String headliner = text(event.get("headline_artist_name"));
            Gig gig = new Gig(title(event, headliner),
                    new GigSchedule(start, end),
                    new Location(venue(text(event.get("venue_name"))), city, country),
                    lineup(event.get("lineup"), headliner),
                    Admission.free(),
                    text(event.get("description")), null, null, null, false);
            String eventId = id instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(id);
            return Optional.of(new ImportedGig(Platform.BANDSINTOWN, gig, eventId));
        } catch (DateTimeException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String title(Map<String, Object> event, String headliner) {
        String title = text(event.get("title"));
        if (title != null) {
            return title;
        }
        return headliner != null ? headliner : "Concert";
    }

    private static String venue(String name) {
        return name == null || name.equalsIgnoreCase("TBA") ? null : name;
    }

    private static List<String> lineup(Object raw, String headliner) {
        List<String> names = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String name = item instanceof Map<?, ?> m ? text(m.get("name")) : text(item);
                if (name != null && !name.equalsIgnoreCase(headliner == null ? "" : headliner)) {
                    names.add(name);
                }
            }
        }
        return names;
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

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || s.equals("null") ? null : s;
    }
}
