package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.EntryType;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Reusable gig entry form (add and edit share it). Knows how to populate itself from
 * a {@link Gig}, validate, and build a {@link Gig}. No persistence concern — the
 * views wire it to the use cases.
 */
class GigForm extends FormLayout {

    private final TextField name = new TextField("Event name");
    private final DatePicker startDate = new DatePicker("Start date");
    private final TimePicker startTime = new TimePicker("Start time");
    private final DatePicker endDate = new DatePicker("End date (optional)");
    private final TimePicker endTime = new TimePicker("End time (optional)");
    private final TextField venue = new TextField("Venue");
    private final TextField city = new TextField("City");
    private final ComboBox<Country> country = new ComboBox<>("Country");
    private final TextField bands = new TextField("Lineup (comma-separated)");
    private final RadioButtonGroup<EntryType> entryType = new RadioButtonGroup<>("Entry");
    private final TextField entryFee = new TextField("Entry fee");
    private final TextArea description = new TextArea("Description");
    private final TextField facebookUrl = new TextField("Facebook event URL");
    private final TextField ticketUrl = new TextField("Ticket URL");
    private final TextField posterUrl = new TextField("Poster image URL");

    GigForm() {
        country.setItems(Country.values());
        country.setItemLabelGenerator(Country::displayName);
        entryType.setItems(EntryType.values());
        entryType.setItemLabelGenerator(GigForm::entryLabel);
        entryFee.setEnabled(false);
        entryType.addValueChangeListener(e -> entryFee.setEnabled(e.getValue() == EntryType.PAID));
        clear();

        add(name, venue, startDate, startTime, endDate, endTime, city, country, bands,
                entryType, entryFee, facebookUrl, ticketUrl, posterUrl, description);
        setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("28rem", 2));
        setColspan(description, 2);
    }

    void clear() {
        name.clear();
        startDate.clear();
        startTime.clear();
        endDate.clear();
        endTime.clear();
        venue.clear();
        city.clear();
        bands.clear();
        entryFee.clear();
        description.clear();
        facebookUrl.clear();
        ticketUrl.clear();
        posterUrl.clear();
        country.setValue(Country.CZECHIA);
        entryType.setValue(EntryType.FREE);
    }

    void populate(Gig gig) {
        name.setValue(gig.title());
        startDate.setValue(gig.schedule().start().toLocalDate());
        startTime.setValue(gig.schedule().start().toLocalTime());
        if (gig.schedule().end() != null) {
            endDate.setValue(gig.schedule().end().toLocalDate());
            endTime.setValue(gig.schedule().end().toLocalTime());
        }
        venue.setValue(gig.location().displayVenue().equals("TBA") ? "" : gig.location().venue());
        city.setValue(gig.location().city());
        country.setValue(gig.location().country());
        bands.setValue(String.join(", ", gig.lineup()));
        entryType.setValue(gig.admission().type());
        entryFee.setValue(nn(gig.admission().amount()));
        description.setValue(nn(gig.description()));
        facebookUrl.setValue(nn(gig.facebookUrl()));
        ticketUrl.setValue(nn(gig.ticketUrl()));
        posterUrl.setValue(nn(gig.posterImageUrl()));
    }

    /** @return an error message if the form is invalid, otherwise {@code null}. */
    String validationError() {
        if (name.isEmpty() || startDate.isEmpty() || startTime.isEmpty()
                || venue.isEmpty() || city.isEmpty() || country.isEmpty()) {
            return "Fill in event name, date, time, venue, city and country";
        }
        if (entryType.getValue() == EntryType.PAID && entryFee.isEmpty()) {
            return "Enter the entry fee, or change the entry type";
        }
        return null;
    }

    Gig toGig() {
        Country c = country.getValue();
        ZoneId zone = ZoneId.of(c.timezone());
        ZonedDateTime start = ZonedDateTime.of(startDate.getValue(), startTime.getValue(), zone);
        ZonedDateTime end = endDate.isEmpty() || endTime.isEmpty()
                ? null : ZonedDateTime.of(endDate.getValue(), endTime.getValue(), zone);
        Admission admission = switch (entryType.getValue()) {
            case FREE -> Admission.free();
            case VOLUNTARY -> Admission.voluntary();
            case PAID -> Admission.paid(entryFee.getValue());
        };
        return Gig.create(name.getValue(), new GigSchedule(start, end),
                new Location(venue.getValue(), city.getValue(), c), parseLineup(bands.getValue()), admission,
                nullIfBlank(description.getValue()), nullIfBlank(facebookUrl.getValue()),
                nullIfBlank(ticketUrl.getValue()), nullIfBlank(posterUrl.getValue()));
    }

    private static List<String> parseLineup(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String nn(String value) {
        return value != null ? value : "";
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String entryLabel(EntryType type) {
        return switch (type) {
            case FREE -> "Free";
            case VOLUNTARY -> "Voluntary";
            case PAID -> "Paid";
        };
    }
}
