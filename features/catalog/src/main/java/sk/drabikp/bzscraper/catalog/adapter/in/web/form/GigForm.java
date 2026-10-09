package sk.drabikp.bzscraper.catalog.adapter.in.web.form;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.EntryType;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.gig.domain.Slot;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.places.application.port.in.FindPlacesUseCase;
import sk.drabikp.bzscraper.places.domain.Town;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reusable gig entry form (add and edit share it). Knows how to populate itself from
 * a {@link Gig}, validate, and build a {@link Gig}. No persistence concern — the
 * views wire it to the use cases.
 */
public class GigForm extends FormLayout {

    private final TextField name = new TextField("Event name");
    private final DatePicker startDate = new DatePicker("Start date");
    private final TimePicker startTime = new TimePicker("Start time");
    private final DatePicker endDate = new DatePicker("End date (optional)");
    private final TimePicker endTime = new TimePicker("End time (optional)");
    private final DatePicker slotDate = new DatePicker("Band's slot: date (optional)");
    private final TimePicker slotTime = new TimePicker("Band's slot: start");
    private final TimePicker slotEndTime = new TimePicker("Band's slot: end (optional)");
    private final TextField venue = new TextField("Venue");
    private final ComboBox<Town> city = new ComboBox<>("City");
    private final TextField street = new TextField("Street (optional)");
    private final TextField postalCode = new TextField("Postal code (optional)");
    private final FindPlacesUseCase places;
    private final ComboBox<Country> country = new ComboBox<>("Country");
    private final TextField bands = new TextField("Lineup (comma-separated)");
    private final RadioButtonGroup<EntryType> entryType = new RadioButtonGroup<>("Entry");
    private final TextField entryFee = new TextField("Entry fee");
    private final TextArea description = new TextArea("Description");
    private final TextField facebookUrl = new TextField("Facebook event URL");
    private final TextField ticketUrl = new TextField("Ticket URL");
    private final TextField posterUrl = new TextField("Poster image URL");

    public GigForm(FindPlacesUseCase places, Platforms platforms) {
        this.places = places;
        city.setItemLabelGenerator(Town::label);
        city.setItems(query -> places.search(query.getFilter().orElse("")).stream()
                .skip(query.getOffset()).limit(query.getLimit()));
        city.setAllowCustomValue(true);
        city.addCustomValueSetListener(e -> city.setValue(Town.typed(e.getDetail(), country.getValue())));
        city.addValueChangeListener(e -> {
            Town town = e.getValue();
            if (town != null && town.resolved()) {
                country.setValue(town.country());
                if (postalCode.isEmpty() && town.postalCode() != null) {
                    postalCode.setValue(town.postalCode());
                }
            }
            describeTown();
        });
        country.setItems(Country.values());
        country.setItemLabelGenerator(Country::displayName);
        entryType.setItems(EntryType.values());
        entryType.setItemLabelGenerator(GigForm::entryLabel);
        entryFee.setEnabled(false);
        entryType.addValueChangeListener(e -> entryFee.setEnabled(e.getValue() == EntryType.PAID));
        slotDate.setHelperText(slotHelp(platforms));
        clear();

        add(name, venue, startDate, startTime, endDate, endTime, slotDate, slotTime, slotEndTime, city, country,
                street, postalCode, bands, entryType, entryFee, facebookUrl, ticketUrl, posterUrl, description);
        setColspan(slotDate, 2);
        setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("28rem", 2));
        setColspan(description, 2);
    }

    /** What the slot is for, and which platforms show it rather than the whole event. */
    private static String slotHelp(Platforms platforms) {
        Map<Boolean, List<String>> bySlot = platforms.all().stream().collect(Collectors.partitioningBy(
                p -> platforms.traits(p).listsBandSlot(), Collectors.mapping(platforms::name, Collectors.toList())));
        String help = "When the band itself plays, e.g. at a festival over several days.";
        if (bySlot.get(true).isEmpty()) {
            return help;
        }
        help += " " + String.join(", ", bySlot.get(true)) + " show" + (bySlot.get(true).size() == 1 ? "s" : "")
                + " the slot";
        return help + (bySlot.get(false).isEmpty() ? "." : "; " + String.join(", ", bySlot.get(false))
                + " the whole event.");
    }

    public void clear() {
        name.clear();
        startDate.clear();
        startTime.clear();
        startTime.setHelperText(null);
        endDate.clear();
        endTime.clear();
        slotDate.clear();
        slotTime.clear();
        slotEndTime.clear();
        venue.clear();
        city.clear();
        street.clear();
        postalCode.clear();
        bands.clear();
        entryFee.clear();
        description.clear();
        facebookUrl.clear();
        ticketUrl.clear();
        posterUrl.clear();
        country.setValue(Country.CZECHIA);
        entryType.setValue(EntryType.FREE);
    }

    public void populate(Gig gig) {
        name.setValue(gig.title());
        startDate.setValue(gig.schedule().start().toLocalDate());
        startTime.setValue(gig.schedule().start().toLocalTime());
        if (gig.schedule().end() != null) {
            endDate.setValue(gig.schedule().end().toLocalDate());
            endTime.setValue(gig.schedule().end().toLocalTime());
        }
        Slot slot = gig.schedule().slot();
        if (slot != null) {
            slotDate.setValue(slot.start().toLocalDate());
            slotTime.setValue(slot.start().toLocalTime());
            if (slot.end() != null) {
                slotEndTime.setValue(slot.end().toLocalTime());
            }
        }
        venue.setValue(gig.location().displayVenue().equals("TBA") ? "" : gig.location().venue());
        city.setValue(Town.of(gig.location()));
        Address address = gig.location().address();
        street.setValue(address != null && address.street() != null ? address.street() : "");
        postalCode.setValue(address != null && address.postalCode() != null ? address.postalCode() : "");
        country.setValue(gig.location().country());
        bands.setValue(String.join(", ", gig.lineup()));
        entryType.setValue(gig.admission().type());
        entryFee.setValue(nn(gig.admission().amount()));
        description.setValue(nn(gig.description()));
        facebookUrl.setValue(nn(gig.facebookUrl()));
        ticketUrl.setValue(nn(gig.ticketUrl()));
        posterUrl.setValue(nn(gig.posterImageUrl()));
    }

    /** Starts a new gig from what a calendar event says; what it doesn't say stays empty for the user. */
    /**
     * What another page knows of a gig to be added (e.g. a calendar event): the user checks and
     * completes it. {@code showTime} is when the band plays; without it, {@code eventStart} is
     * offered as a hint.
     */
    public record Draft(String title, LocalDate date, LocalTime showTime, LocalTime eventStart, String venue,
                        String city, Country country, String street, String postalCode) {
    }

    public void prefill(Draft draft) {
        clear();
        name.setValue(draft.title());
        startDate.setValue(draft.date());
        if (draft.showTime() != null) {
            startTime.setValue(draft.showTime());
        } else if (draft.eventStart() != null) {
            startTime.setHelperText("No show time in the notes; the event starts at " + draft.eventStart()
                    + " (usually the arrival)");
        }
        venue.setValue(draft.venue() == null ? "" : draft.venue());
        if (draft.city() != null) {
            // the calendar's postal code tells same-named towns apart; one fit → picked
            city.setValue(places.resolve(draft.city(), draft.country(), draft.postalCode())
                    .orElse(Town.typed(draft.city(), draft.country())));
        }
        street.setValue(draft.street() == null ? "" : draft.street());
        postalCode.setValue(draft.postalCode() == null ? "" : draft.postalCode());
        if (draft.country() != null) {
            country.setValue(draft.country());
        } else {
            country.clear();
        }
    }

    /**
     * Applies the calendar's show — the BAND's day and time: to the band's slot when the gig
     * has one or the event runs over several days, otherwise to the event's start
     * ({@code moveDay}: the day changed, not only the time).
     */
    public void applyShow(LocalDate day, LocalTime time, boolean moveDay) {
        if (!slotDate.isEmpty() || multiDay()) {
            slotDate.setValue(day);
            if (time != null) {
                slotTime.setValue(time);
            }
        } else {
            reschedule(moveDay ? day : null, time);
        }
    }

    private boolean multiDay() {
        if (startDate.isEmpty() || endDate.isEmpty()) {
            return false;
        }
        LocalDate last = endDate.getValue();
        if (LocalTime.MIDNIGHT.equals(endTime.getValue())) {
            last = last.minusDays(1);
        }
        return last.isAfter(startDate.getValue());
    }

    /** Under the city: which town it is, or that it wasn't picked from the list. */
    private void describeTown() {
        Town town = city.getValue();
        city.setHelperText(town == null ? null : town.resolved()
                ? town.district() + (town.region() != null ? ", " + town.region() : "")
                : "Not picked from the list — a platform may choose another town of that name");
    }

    /** Moves the start to another day and/or time (null = keep), keeping the length when there is an end. */
    void reschedule(LocalDate day, LocalTime time) {
        LocalDate oldDay = startDate.getValue();
        if (day != null) {
            if (!endDate.isEmpty() && oldDay != null) {
                endDate.setValue(endDate.getValue().plusDays(ChronoUnit.DAYS.between(oldDay, day)));
            }
            startDate.setValue(day);
        }
        if (time != null) {
            startTime.setValue(time);
        }
    }

    /** @return an error message if the form is invalid, otherwise {@code null}. */
    public String validationError() {
        if (name.isEmpty() || startDate.isEmpty() || startTime.isEmpty()
                || venue.isEmpty() || city.isEmpty() || country.isEmpty()) {
            return "Fill in event name, date, time, venue, city and country";
        }
        if (entryType.getValue() == EntryType.PAID && entryFee.isEmpty()) {
            return "Enter the entry fee, or change the entry type";
        }
        if (slotDate.isEmpty() != slotTime.isEmpty() || slotDate.isEmpty() && !slotEndTime.isEmpty()) {
            return "Give the band's slot a date and a start time, or leave all its fields empty";
        }
        try {
            toGig();
        } catch (IllegalArgumentException e) {
            return "Check the dates: " + e.getMessage();
        }
        return null;
    }

    public Gig toGig() {
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
        Slot slot = null;
        if (!slotDate.isEmpty() && !slotTime.isEmpty()) {
            ZonedDateTime slotStart = ZonedDateTime.of(slotDate.getValue(), slotTime.getValue(), zone);
            ZonedDateTime slotEnd = null;
            if (!slotEndTime.isEmpty()) {
                slotEnd = ZonedDateTime.of(slotDate.getValue(), slotEndTime.getValue(), zone);
                if (slotEnd.isBefore(slotStart)) {
                    slotEnd = slotEnd.plusDays(1);          // a set past midnight
                }
            }
            slot = new Slot(slotStart, slotEnd);
        }
        Town town = city.getValue();
        Address address = town.address(nullIfBlank(street.getValue()), nullIfBlank(postalCode.getValue()));
        return Gig.create(name.getValue(), new GigSchedule(start, end, slot),
                new Location(venue.getValue(), town.name(), c,
                        address.equals(new Address(null, null, null, null, null, null)) ? null : address),
                parseLineup(bands.getValue()), admission,
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
