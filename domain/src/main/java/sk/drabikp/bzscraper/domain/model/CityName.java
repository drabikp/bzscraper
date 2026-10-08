package sk.drabikp.bzscraper.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Compares city names as platforms write them — some in English ("Prague", "Pilsen"), some
 * with a district number ("Vsetín 1"), some in the local language ("Praha", "Plzeň").
 * Accents, case, spacing and a trailing district number are ignored, and the English names
 * of Czech and Slovak cities count as the local ones.
 */
public final class CityName {

    private static final Map<String, String> ENGLISH_NAMES = Map.of(
            "prague", "praha",
            "pilsen", "plzen",
            "budweis", "ceske budejovice",
            "olmutz", "olomouc",
            "brunn", "brno");

    private CityName() {
    }

    /** A comparison key: equal keys = the same city. */
    public static String key(String city) {
        if (city == null) {
            return "";
        }
        String plain = Normalizer.normalize(city, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ")
                .replaceAll(" \\d+$", "");
        return ENGLISH_NAMES.getOrDefault(plain, plain);
    }

    public static boolean same(String a, String b) {
        return key(a).equals(key(b));
    }
}
