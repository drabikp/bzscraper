package sk.drabikp.bzscraper.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a calendar event says about the gig, as far as it can be read: the start of a gig
 * form, never a finished {@link Gig} — the user checks it. {@code showTime} comes from the
 * notes' show-time label ({@link RuleKind#SHOWTIME_LABEL}) and is null when there is none;
 * {@code date} is the show's day (a show after midnight is on the next day). {@code venue},
 * {@code city} and {@code country} are read from the event's place and are null when it
 * doesn't say. {@code eventStart} is the event's own start time (usually the arrival), null
 * for all-day events.
 */
public record CalendarGigDraft(String eventId, String title, LocalDate date, LocalTime showTime,
                               String venue, String city, Country country, LocalTime eventStart) {
}
