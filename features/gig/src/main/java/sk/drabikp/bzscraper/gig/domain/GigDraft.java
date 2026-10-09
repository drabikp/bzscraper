package sk.drabikp.bzscraper.gig.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A gig as a form holds it — days and times as the band reads them in the gig's own country,
 * the town's address details when it was picked from the place search — before it is a
 * {@link Gig}. {@link #problems()} names what is missing; {@link #toGig()} builds the gig.
 *
 * @param endDate     null with an {@code endTime}: the day it starts, or the next one when the
 *                    end is earlier than the start (a night that runs past midnight)
 * @param slotDate    when the band itself plays (a festival over several days); with {@code slotTime}
 * @param slotEndTime the band's set ends; past midnight when earlier than {@code slotTime}
 * @param venue       null or blank: not known yet (the gig is then known by its town)
 */
public record GigDraft(String title, LocalDate date, LocalTime time, LocalDate endDate, LocalTime endTime,
                       LocalDate slotDate, LocalTime slotTime, LocalTime slotEndTime,
                       String venue, String city, Country country,
                       String street, String postalCode, String district, String region,
                       Double latitude, Double longitude,
                       List<String> lineup, EntryType entry, String price,
                       String description, String facebookUrl, String ticketUrl, String posterUrl,
                       boolean cancelled) {

    public GigDraft {
        lineup = lineup == null ? List.of()
                : lineup.stream().filter(b -> b != null && !b.isBlank()).map(String::strip).toList();
        entry = entry == null ? EntryType.FREE : entry;
    }

    /** The draft of an existing gig, to edit it. */
    public static GigDraft of(Gig gig) {
        GigSchedule schedule = gig.schedule();
        Location location = gig.location();
        Address address = location.address();
        Slot slot = schedule.slot();
        ZonedDateTime end = schedule.end();
        return new GigDraft(gig.title(), schedule.start().toLocalDate(), schedule.start().toLocalTime(),
                end != null ? end.toLocalDate() : null, end != null ? end.toLocalTime() : null,
                slot != null ? slot.start().toLocalDate() : null, slot != null ? slot.start().toLocalTime() : null,
                slot != null && slot.end() != null ? slot.end().toLocalTime() : null,
                location.venue(), location.city(), location.country(),
                address != null ? address.street() : null, address != null ? address.postalCode() : null,
                address != null ? address.district() : null, address != null ? address.region() : null,
                address != null ? address.latitude() : null, address != null ? address.longitude() : null,
                gig.lineup(), gig.admission().type(), gig.admission().amount(),
                gig.description(), gig.facebookUrl(), gig.ticketUrl(), gig.posterImageUrl(), gig.cancelled());
    }

    /** The fields that are missing or don't fit together; empty when it is a gig. */
    public List<String> problems() {
        List<String> missing = new ArrayList<>();
        if (blank(title)) {
            missing.add("title");
        }
        if (date == null) {
            missing.add("date");
        }
        if (time == null) {
            missing.add("time");
        }
        if (blank(city)) {
            missing.add("city");
        }
        if (country == null) {
            missing.add("country");
        }
        if (entry == EntryType.PAID && blank(price)) {
            missing.add("price");
        }
        if ((slotDate == null) != (slotTime == null) || slotDate == null && slotEndTime != null) {
            missing.add("slot");
        }
        if (endDate != null && endTime == null) {
            missing.add("endTime");
        }
        if (missing.isEmpty()) {
            try {
                toGig();
            } catch (IllegalArgumentException e) {
                missing.add("dates");
            }
        }
        return missing;
    }

    /** The gig; IllegalArgumentException when it can't be one (see {@link #problems()}). */
    public Gig toGig() {
        if (blank(title) || date == null || time == null || blank(city) || country == null) {
            throw new IllegalArgumentException("a gig needs its name, day, time, town and country");
        }
        ZoneId zone = ZoneId.of(country.timezone());
        ZonedDateTime start = ZonedDateTime.of(date, time, zone);
        ZonedDateTime end = null;
        if (endTime != null) {
            end = ZonedDateTime.of(endDate != null ? endDate : date, endTime, zone);
            if (endDate == null && !end.isAfter(start)) {
                end = end.plusDays(1);
            }
        }
        Slot slot = null;
        if (slotDate != null && slotTime != null) {
            ZonedDateTime slotStart = ZonedDateTime.of(slotDate, slotTime, zone);
            ZonedDateTime slotEnd = null;
            if (slotEndTime != null) {
                slotEnd = ZonedDateTime.of(slotDate, slotEndTime, zone);
                if (slotEnd.isBefore(slotStart)) {
                    slotEnd = slotEnd.plusDays(1);
                }
            }
            slot = new Slot(slotStart, slotEnd);
        }
        Admission admission = switch (entry) {
            case FREE -> Admission.free();
            case VOLUNTARY -> Admission.voluntary();
            case PAID -> Admission.paid(price.strip());
        };
        Address address = new Address(nullIfBlank(street), nullIfBlank(postalCode), nullIfBlank(district),
                nullIfBlank(region), latitude, longitude);
        Gig gig = Gig.create(title, new GigSchedule(start, end, slot),
                new Location(nullIfBlank(venue), city.strip(), country,
                        address.equals(new Address(null, null, null, null, null, null)) ? null : address),
                lineup, admission, nullIfBlank(description), nullIfBlank(facebookUrl), nullIfBlank(ticketUrl),
                nullIfBlank(posterUrl));
        return cancelled ? gig.cancel() : gig;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String nullIfBlank(String text) {
        return blank(text) ? null : text.strip();
    }
}
