package sk.drabikp.bzscraper.gig.api;

import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.EntryType;
import sk.drabikp.bzscraper.gig.domain.GigDraft;

import java.time.LocalTime;

/**
 * The API's shared parts (generated from gig.yaml) to the kernel's domain and back: a gig as
 * the form holds it ({@link GigDraftJson} ↔ {@link GigDraft}), its country and entry type.
 * Times travel as "HH:mm" (or "HH:mm:ss").
 */
public final class GigDraftMapping {

    private GigDraftMapping() {
    }

    public static GigDraft toDraft(GigDraftJson json) {
        return new GigDraft(json.getTitle(), json.getDate(), time(json.getTime()), json.getEndDate(),
                time(json.getEndTime()), json.getSlotDate(), time(json.getSlotTime()), time(json.getSlotEndTime()),
                json.getVenue(), json.getCity(), toCountry(json.getCountry()),
                json.getStreet(), json.getPostalCode(), json.getDistrict(), json.getRegion(),
                json.getLatitude(), json.getLongitude(), json.getLineup(),
                json.getEntry() == null ? null : EntryType.valueOf(json.getEntry().getValue()), json.getPrice(),
                json.getDescription(), json.getFacebookUrl(), json.getTicketUrl(), json.getPosterUrl(),
                Boolean.TRUE.equals(json.getCancelled()));
    }

    public static GigDraftJson toJson(GigDraft draft) {
        return new GigDraftJson(draft.title(), draft.date(), text(draft.time()), draft.endDate(),
                text(draft.endTime()), draft.slotDate(), text(draft.slotTime()), text(draft.slotEndTime()),
                draft.venue(), draft.city(), toJson(draft.country()), draft.street(), draft.postalCode(),
                draft.district(), draft.region(), draft.latitude(), draft.longitude(), draft.lineup(),
                EntryTypeJson.fromValue(draft.entry().name()), draft.price(), draft.description(),
                draft.facebookUrl(), draft.ticketUrl(), draft.posterUrl(), draft.cancelled());
    }

    public static Country toCountry(CountryJson json) {
        return json == null ? null : Country.valueOf(json.getValue());
    }

    public static CountryJson toJson(Country country) {
        return country == null ? null : CountryJson.fromValue(country.name());
    }

    /** A time of day as the API sends it: "20:30". */
    public static String text(LocalTime time) {
        return time == null ? null : time.toString();
    }

    private static LocalTime time(String text) {
        return text == null || text.isBlank() ? null : LocalTime.parse(text);
    }
}
