package sk.drabikp.bzscraper.csv;

import com.opencsv.CSVWriter;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.gigsummary.GigSummary;

import java.io.IOException;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class GigSummaryCsvConverter {

    private static final String[] CSV_HEADER = {
            "Artist Name", "Venue*", "Country*", "Address", "City*", "Region*", "Postal Code", "Timezone",
            "Start Date* (yyyy-mm-dd)", "Start Time* (HH:MM)", "End Date", "End Time", "Streaming Link",
            "Ticket Link", "Ticket Type", "Ticket Link 2", "Ticket Type 2", "On-Sale Date", "On-Sale Time",
            "Lineup", "Event Name", "Event Display Format", "Description", "Schedule Date", "Schedule Time",
            "Do Not Announce", "Setlist", "Event Image"
    };

    public String convertToCsv(List<GigSummary> gigSummaries) {
        StringWriter writer = new StringWriter();

        try (CSVWriter csvWriter = new CSVWriter(writer)) {


            // Write the header
            csvWriter.writeNext(CSV_HEADER);

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

            for (GigSummary gigSummary : gigSummaries) {
                List<String> bands = gigSummary.bands();
                String[] rowData = {
                        //bands == null ? gigSummary.title() : bands.get(0), //FIXME
                        "Eufory (Band)",
                        StringUtils.isEmpty(gigSummary.venue()) ? "TBA" : gigSummary.venue(),
                        resolveCountry(gigSummary.city()),
                        "",  // Country*, Address (empty fields) // TODO resolver na krajinu
                        resolveCity(gigSummary.city()),
                        "", "",  // Region*, Postal Code (empty fields)
                        "",   // Timezone
                        gigSummary.start().format(dateFormatter),  // Start Date* (yyyy-mm-dd)
                        gigSummary.start() != null? gigSummary.start().format(timeFormatter) : "20:00",  // Start Time* (HH:MM)
                        gigSummary.end() != null ? gigSummary.end().format(dateFormatter) : "",  // End Date
                        gigSummary.end() != null ? gigSummary.end().format(timeFormatter) : "",  // End Time
                        "", "",  // Streaming Link, Ticket Link (empty fields)
                        "", "",  // Ticket Type, Ticket Link 2 (empty fields)
                        "", "",  // Ticket Type 2, On-Sale Date (empty fields)
                        "", String.join(",", Optional.ofNullable(gigSummary.bands()).orElseGet(List::of)),  // On-Sale Time, Lineup (empty fields)
                        gigSummary.title(),  // Event Name
                        "", "",  // Event Display Format, Description (empty fields)
                        "", "",  // Schedule Date, Schedule Time (empty fields)
                        "", "",  // Do Not Announce, Setlist (empty fields)
                        ""  // Event Image (empty field)
                };

                csvWriter.writeNext(rowData);
            }

        } catch (IOException e) {
            throw  new RuntimeException(e);
        }

        return writer.toString();
    }

    private String resolveCity(String city) {
        if (city.contains(", ČR")) {
            return StringUtils.remove(city, ", ČR");
        } else if (city.contains(", SK")) {
            return StringUtils.remove(city, ", SK");
        }
        return city;
    }

    private String resolveCountry(String city) {
        if (city.contains(", ČR")) {
            return "Czech Republic";
        } else if (city.contains(", SK")) {
            return "Slovakia";
        }
        return "-";
    }
}