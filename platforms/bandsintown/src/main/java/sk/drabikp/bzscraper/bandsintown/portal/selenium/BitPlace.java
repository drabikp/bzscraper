package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import java.util.Map;

/**
 * A suggestion of the portal's venue search (Google places): its id, the place's name and the
 * full description the venue field shows once picked ("Secret Garden (Skrytý Dvor),
 * Moyzesova, Košice, Slovakia").
 */
record BitPlace(String placeId, String name, String description) {

    static BitPlace of(Map<String, ?> raw) {
        return new BitPlace(String.valueOf(raw.get("place_id")), String.valueOf(raw.get("name")),
                String.valueOf(raw.get("description")));
    }
}
