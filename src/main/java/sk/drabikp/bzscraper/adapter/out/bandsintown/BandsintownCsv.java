package sk.drabikp.bzscraper.adapter.out.bandsintown;

import com.opencsv.CSVWriter;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Bandsintown's bulk-upload CSV. The 28 template columns (starred = required) are what
 * the artist portal's template and our download export contain; the portal's own
 * export appends {@code Event Id} and {@code Status}, and uploading a row WITH an
 * Event Id updates that event in place instead of creating a new one.
 *
 * <p>"Do Not Announce = Y" makes Bandsintown treat the event as already announced, so
 * publishing it sends followers no notification.
 */
public final class BandsintownCsv {

    static final String[] TEMPLATE_HEADER = {
            "Artist Name", "Venue*", "Country*", "Address", "City*", "Region*", "Postal Code", "Timezone*",
            "Start Date* (yyyy-mm-dd)", "Start Time* (HH:MM)", "End Date", "End Time", "Streaming Link",
            "Ticket Link", "Ticket Type", "Ticket Link 2", "Ticket Type 2", "On-Sale Date", "On-Sale Time",
            "Lineup", "Event Name", "Event Display Format", "Description", "Schedule Date", "Schedule Time",
            "Do Not Announce", "Setlist", "Event Image"
    };
    private static final int DO_NOT_ANNOUNCE = 25;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private BandsintownCsv() {
    }

    /** The plain template, as the user downloads it for a manual import. */
    public static String template(List<Gig> gigs, String artistName) {
        List<String[]> rows = new ArrayList<>();
        gigs.forEach(gig -> rows.add(row(gig, artistName)));
        return write(TEMPLATE_HEADER, rows);
    }

    /** New events to upload; {@code announce=false} marks every row "Do Not Announce". */
    public static String newEvents(List<Gig> gigs, String artistName, boolean announce) {
        List<String[]> rows = new ArrayList<>();
        for (Gig gig : gigs) {
            String[] row = row(gig, artistName);
            row[DO_NOT_ANNOUNCE] = announce ? "" : "Y";
            rows.add(row);
        }
        return write(TEMPLATE_HEADER, rows);
    }

    /**
     * Edits of existing, published events, keyed by Bandsintown event id. Never announced:
     * an edit must not re-notify followers.
     */
    public static String updates(Map<String, Gig> gigsByEventId, String artistName) {
        String[] header = Arrays.copyOf(TEMPLATE_HEADER, TEMPLATE_HEADER.length + 2);
        header[TEMPLATE_HEADER.length] = "Event Id";
        header[TEMPLATE_HEADER.length + 1] = "Status";
        List<String[]> rows = new ArrayList<>();
        gigsByEventId.forEach((eventId, gig) -> {
            String[] row = Arrays.copyOf(row(gig, artistName), header.length);
            row[DO_NOT_ANNOUNCE] = "Y";
            row[TEMPLATE_HEADER.length] = eventId;
            row[TEMPLATE_HEADER.length + 1] = "PUBLISHED";
            rows.add(row);
        });
        return write(header, rows);
    }

    private static String[] row(Gig gig, String artistName) {
        ZonedDateTime start = gig.schedule().start();        // always present (invariant)
        ZonedDateTime end = gig.schedule().end();
        String ticketLink = nullToEmpty(gig.ticketUrl());
        return new String[]{
                nullToEmpty(artistName),                                        // Artist Name
                gig.location().displayVenue(),                                  // Venue*
                gig.location().countryName(),                                   // Country*
                "",                                                             // Address
                gig.location().city(),                                          // City*
                "",                                                             // Region*
                "",                                                             // Postal Code
                gig.location().timezone(),                                      // Timezone*
                start.format(DATE),                                             // Start Date*
                start.format(TIME),                                             // Start Time*
                end != null ? end.format(DATE) : "",                            // End Date
                end != null ? end.format(TIME) : "",                            // End Time
                "",                                                             // Streaming Link
                ticketLink,                                                     // Ticket Link
                ticketLink.isBlank() ? "" : "Tickets",                          // Ticket Type
                "",                                                             // Ticket Link 2
                "",                                                             // Ticket Type 2
                "",                                                             // On-Sale Date
                "",                                                             // On-Sale Time
                String.join(",", gig.lineup()),                                 // Lineup
                gig.title(),                                                    // Event Name
                "",                                                             // Event Display Format
                nullToEmpty(gig.description()),                                 // Description
                "",                                                             // Schedule Date
                "",                                                             // Schedule Time
                "",                                                             // Do Not Announce
                "",                                                             // Setlist
                nullToEmpty(gig.posterImageUrl())                               // Event Image
        };
    }

    private static String write(String[] header, List<String[]> rows) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csv = new CSVWriter(writer)) {
            csv.writeNext(header);
            rows.forEach(csv::writeNext);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
