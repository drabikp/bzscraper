package sk.drabikp.bzscraper.adapter.out.csv;

import com.opencsv.CSVWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Maps {@link Gig} aggregates to Bandsintown's 28-column bulk-import CSV. Column
 * order and header mirror the import template (starred = required). Created gigs
 * upload with a BLANK Event Id (the importer assigns one), so there is intentionally
 * no Event Id column. Country/Timezone come straight from the gig's {@link Gig#location()}.
 */
@Component
public class OpenCsvGigExporter implements GigCsvExporter {

    private static final String[] CSV_HEADER = {
            "Artist Name", "Venue*", "Country*", "Address", "City*", "Region*", "Postal Code", "Timezone*",
            "Start Date* (yyyy-mm-dd)", "Start Time* (HH:MM)", "End Date", "End Time", "Streaming Link",
            "Ticket Link", "Ticket Type", "Ticket Link 2", "Ticket Type 2", "On-Sale Date", "On-Sale Time",
            "Lineup", "Event Name", "Event Display Format", "Description", "Schedule Date", "Schedule Time",
            "Do Not Announce", "Setlist", "Event Image"
    };

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final String artistName;

    public OpenCsvGigExporter(
            @Value("${bzscraper.bandsintown.artist-name:Eufory (Band)}") String artistName) {
        this.artistName = artistName;
    }

    @Override
    public String export(List<Gig> gigs) {
        StringWriter writer = new StringWriter();

        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(CSV_HEADER);
            for (Gig gig : gigs) {
                csvWriter.writeNext(toRow(gig));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return writer.toString();
    }

    private String[] toRow(Gig gig) {
        ZonedDateTime start = gig.schedule().start();        // always present (invariant)
        ZonedDateTime end = gig.schedule().end();
        String endDate = end != null ? end.format(DATE_FORMATTER) : "";
        String endTime = end != null ? end.format(TIME_FORMATTER) : "";
        String ticketLink = nullToEmpty(gig.ticketUrl());

        return new String[]{
                artistName,                                                     // Artist Name
                gig.location().displayVenue(),                                  // Venue*
                gig.location().countryName(),                                   // Country*
                "",                                                             // Address
                gig.location().city(),                                          // City*
                "",                                                             // Region*
                "",                                                             // Postal Code
                gig.location().timezone(),                                      // Timezone*
                start.format(DATE_FORMATTER),                                   // Start Date*
                start.format(TIME_FORMATTER),                                   // Start Time*
                endDate,                                                        // End Date
                endTime,                                                        // End Time
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
                nullToEmpty(gig.posterImageUrl())                              // Event Image
        };
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
