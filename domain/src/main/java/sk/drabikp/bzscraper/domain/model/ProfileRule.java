package sk.drabikp.bzscraper.domain.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One rule of a band profile: a {@link RuleKind}, its value (words, a number or a
 * {@code label=prefix} field, depending on the kind), the weight it adds to an event's
 * score when it matches (0 for role kinds) and where it came from.
 */
public record ProfileRule(RuleKind kind, String value, int weight, RuleOrigin origin, boolean enabled) {

    public ProfileRule {
        if (kind == null || origin == null) {
            throw new IllegalArgumentException("rule kind and origin are required");
        }
        value = value == null || value.isBlank() ? null : value.strip();
        switch (kind.value()) {
            case NONE -> value = null;
            case TEXT -> {
                if (value == null || alternativesOf(value).isEmpty()) {
                    throw new IllegalArgumentException(kind + " needs one or more words");
                }
            }
            case NUMBER -> {
                if (value == null || !value.matches("\\d+")) {
                    throw new IllegalArgumentException(kind + " needs a whole number");
                }
            }
            case FIELD -> {
                if (value == null || !value.matches("[^=]+=[^=]+")) {
                    throw new IllegalArgumentException(kind + " needs label=prefix");
                }
            }
        }
        if (!kind.weighted()) {
            weight = 0;
        }
    }

    public static ProfileRule preset(RuleKind kind, String value, int weight) {
        return new ProfileRule(kind, value, weight, RuleOrigin.PRESET, true);
    }

    /** The value's alternatives, folded for comparison (see {@link #fold}). */
    public List<String> alternatives() {
        return value == null ? List.of() : alternativesOf(value);
    }

    public int number() {
        return Integer.parseInt(value);
    }

    /** For {@link RuleKind.Value#FIELD}: the label and the prefix, folded. */
    public String fieldLabel() {
        return fold(value.substring(0, value.indexOf('=')));
    }

    public String fieldPrefix() {
        return fold(value.substring(value.indexOf('=') + 1));
    }

    /** Lower case, no accents, single spaces — how calendar text and rule values are compared. */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace(' ', ' ').replaceAll("\\s+", " ").strip();
    }

    private static List<String> alternativesOf(String value) {
        return Arrays.stream(value.split("\\|")).map(ProfileRule::fold).filter(s -> !s.isEmpty()).toList();
    }
}
