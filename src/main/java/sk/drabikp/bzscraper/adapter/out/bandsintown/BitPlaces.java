package sk.drabikp.bzscraper.adapter.out.bandsintown;

import sk.drabikp.bzscraper.domain.model.CityName;
import sk.drabikp.bzscraper.domain.model.ProfileRule;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Picks the gig's place among Bandsintown's venue suggestions (Google places, each
 * {@code {place_id, name, description: "Name, Street, Town, Country", address}}): only a
 * place in the gig's town, and among those the one named like the venue, else the first.
 */
final class BitPlaces {

    private BitPlaces() {
    }

    static Map<String, Object> choose(List<Map<String, Object>> places, String venue, String city) {
        List<Map<String, Object>> inTown = places.stream().filter(p -> inTown(p, city)).toList();
        if (venue != null) {
            String wanted = ProfileRule.fold(venue);
            for (Map<String, Object> place : inTown) {
                String name = ProfileRule.fold(String.valueOf(place.get("name")));
                if (name.contains(wanted) || wanted.contains(name)) {
                    return place;
                }
            }
        }
        return inTown.isEmpty() ? null : inTown.getFirst();
    }

    /** One of the description's comma-separated parts is the town (accents, district numbers ignored). */
    private static boolean inTown(Map<String, Object> place, String city) {
        String key = CityName.key(city);
        return Arrays.stream(String.valueOf(place.get("description")).split(","))
                .map(String::strip)
                .anyMatch(part -> CityName.key(part).equals(key));
    }
}
