package sk.drabikp.bzscraper.calendar.domain.rules;

import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventStatus;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification.Reason;
import sk.drabikp.bzscraper.gig.domain.TextFold;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure domain service: sorts calendar events into gig / unsure / not a gig with a band's
 * {@link BandProfile}. Every matching weighted rule adds its weight to the event's score
 * and is kept as a reason; the thresholds turn the score into a suggestion. The user's
 * remembered verdict (by event id) always wins over the suggestion.
 * <ul>
 *   <li>An event with some evidence for a gig and none strongly against is never
 *       suggested as "not a gig" — it is left to the user.</li>
 *   <li>{@link RuleKind#CATALOG_GIG_SAME_DAY} doesn't count when strong evidence against
 *       already matched: travel and rehearsals on a gig day stay what they are.</li>
 * </ul>
 * The status (confirmed / tentative / cancelled) is decided separately, from the
 * calendar's own status and the profile's status rules.
 */
public final class CalendarEventClassifier {

    private static final Duration TRAVEL_ARRIVAL = Duration.ofHours(1);

    private CalendarEventClassifier() {
    }

    public static List<CalendarClassification> classify(List<CalendarEvent> events, BandProfile profile,
                                                         Set<LocalDate> catalogDays,
                                                         Map<String, CalendarEventKind> decisions) {
        Context context = new Context(profile, catalogDays,
                events.stream().collect(Collectors.groupingBy(e -> TextFold.fold(e.title()), Collectors.counting())),
                events.stream().collect(Collectors.groupingBy(CalendarEvent::day)));
        return events.stream().map(e -> classify(e, context, decisions.get(e.id()))).toList();
    }

    private record Context(BandProfile profile, Set<LocalDate> catalogDays, Map<String, Long> titleCounts,
                           Map<LocalDate, List<CalendarEvent>> byDay) {
    }

    private static CalendarClassification classify(CalendarEvent event, Context context, CalendarEventKind verdict) {
        BandProfile profile = context.profile();
        BandProfile.Thresholds thresholds = profile.thresholds();

        List<Reason> reasons = new ArrayList<>();
        for (ProfileRule rule : profile.rules()) {
            if (rule.enabled() && rule.kind().weighted() && rule.kind() != RuleKind.CATALOG_GIG_SAME_DAY) {
                match(rule, event, context).ifPresent(text -> reasons.add(new Reason(text, rule.weight())));
            }
        }
        boolean strongAgainst = reasons.stream().anyMatch(r -> r.weight() <= thresholds.strongNegative());
        if (!strongAgainst && context.catalogDays().contains(event.day())) {
            profile.enabled(RuleKind.CATALOG_GIG_SAME_DAY).stream().findFirst()
                    .ifPresent(rule -> reasons.add(new Reason("the catalog has a gig that day", rule.weight())));
        }
        int score = reasons.stream().mapToInt(Reason::weight).sum();
        boolean anyFor = reasons.stream().anyMatch(r -> r.weight() > 0);

        CalendarEventKind suggested;
        if (score >= thresholds.gigScore()) {
            suggested = CalendarEventKind.GIG;
        } else if (score <= thresholds.notGigScore() && (!anyFor || strongAgainst)) {
            suggested = CalendarEventKind.NOT_GIG;
        } else {
            suggested = CalendarEventKind.UNSURE;
        }

        CalendarEventStatus status = status(event, profile, reasons);
        boolean decided = verdict != null && verdict != CalendarEventKind.UNSURE;
        return new CalendarClassification(event, decided ? verdict : suggested, suggested, decided, status, score,
                reasons);
    }

    private static Optional<String> match(ProfileRule rule, CalendarEvent event, Context context) {
        return switch (rule.kind()) {
            case TITLE_STARTS_WITH -> startsWith(rule, event.title()).map(w -> "title starts with \"" + w + "\"");
            case MEMBER -> startsWith(rule, event.title()).map(w -> "title starts with a member's name \"" + w + "\"");
            case TITLE_CONTAINS -> contains(rule, event.title()).map(w -> "title contains \"" + w + "\"");
            case NOTES_CONTAIN -> contains(rule, event.notes()).map(w -> "notes contain \"" + w + "\"");
            case NOTES_LABEL -> rule.alternatives().stream()
                    .filter(label -> labelPattern(label).matcher(TextFold.fold(event.notes())).find())
                    .findFirst().map(label -> "notes have \"" + label + ":\"");
            case REPEATING -> event.repeating() ? Optional.of("repeating event") : Optional.empty();
            case LONGER_THAN_DAYS -> {
                long days = Duration.between(event.start(), event.end()).toDays();
                yield days > rule.number() ? Optional.of(days + " days long") : Optional.empty();
            }
            case ALL_DAY_FREE -> event.allDay() && event.shownAsFree()
                    ? Optional.of("all-day, time shown as free") : Optional.empty();
            case TITLE_REPEATED -> {
                long uses = context.titleCounts().getOrDefault(TextFold.fold(event.title()), 0L);
                yield uses >= rule.number() ? Optional.of("title used " + uses + " times") : Optional.empty();
            }
            case TRAVEL_LEADS_TO -> travelTo(event, context).map(t -> "travel \"" + t.title() + "\" leads to it");
            default -> Optional.empty();
        };
    }

    private static Optional<CalendarEvent> travelTo(CalendarEvent event, Context context) {
        List<ProfileRule> travel = context.profile().enabled(RuleKind.TRAVEL);
        if (isTravel(event, travel)) {
            return Optional.empty();
        }
        return context.byDay().getOrDefault(event.day(), List.of()).stream()
                .filter(other -> other != event && isTravel(other, travel))
                .filter(other -> (!event.location().isEmpty()
                        && TextFold.fold(other.location()).equals(TextFold.fold(event.location())))
                        || Duration.between(other.end(), event.start()).abs().compareTo(TRAVEL_ARRIVAL) <= 0)
                .findFirst();
    }

    private static boolean isTravel(CalendarEvent event, Collection<ProfileRule> travelRules) {
        return travelRules.stream().anyMatch(rule -> startsWith(rule, event.title()).isPresent());
    }

    private static CalendarEventStatus status(CalendarEvent event, BandProfile profile, List<Reason> reasons) {
        if (event.calendarStatus() == CalendarEventStatus.CANCELLED) {
            reasons.add(new Reason("cancelled in the calendar", 0));
            return CalendarEventStatus.CANCELLED;
        }
        Optional<String> cancelled = firstMatch(profile.enabled(RuleKind.CANCELLED_TITLE),
                rule -> prefix(rule, event.title()).map(w -> "cancelled: title starts with \"" + w + "\""))
                .or(() -> firstMatch(profile.enabled(RuleKind.CANCELLED_NOTES),
                        rule -> contains(rule, event.notes()).map(w -> "cancelled: notes contain \"" + w + "\"")));
        if (cancelled.isPresent()) {
            reasons.add(new Reason(cancelled.get(), 0));
            return CalendarEventStatus.CANCELLED;
        }
        Optional<String> tentative = event.calendarStatus() == CalendarEventStatus.TENTATIVE
                ? Optional.of("tentative in the calendar")
                : firstMatch(profile.enabled(RuleKind.TENTATIVE_TITLE),
                        rule -> prefix(rule, event.title()).map(w -> "tentative: title starts with \"" + w + "\""))
                .or(() -> firstMatch(profile.enabled(RuleKind.TENTATIVE_NOTES),
                        rule -> contains(rule, event.notes()).map(w -> "tentative: notes contain \"" + w + "\"")))
                .or(() -> firstMatch(profile.enabled(RuleKind.CONFIRMED_FIELD), rule -> unconfirmedField(rule, event)));
        if (tentative.isPresent()) {
            reasons.add(new Reason(tentative.get(), 0));
            return CalendarEventStatus.TENTATIVE;
        }
        return CalendarEventStatus.CONFIRMED;
    }

    private static Optional<String> unconfirmedField(ProfileRule rule, CalendarEvent event) {
        Matcher field = Pattern.compile("(?<![\\p{L}\\p{N}])" + spaced(rule.fieldLabel()) + "\\s*:[ \\t]*(\\S+)")
                .matcher(TextFold.fold(event.notes()));
        if (field.find() && !field.group(1).startsWith(rule.fieldPrefix())) {
            return Optional.of("tentative: \"" + rule.fieldLabel() + ":\" says \"" + field.group(1) + "\"");
        }
        return Optional.empty();
    }

    private static Optional<String> firstMatch(List<ProfileRule> rules, Function<ProfileRule, Optional<String>> test) {
        return rules.stream().map(test).flatMap(Optional::stream).findFirst();
    }

    /** The title without leading punctuation ("?Songwriting", "- Fest") starts with an alternative. */
    private static Optional<String> startsWith(ProfileRule rule, String text) {
        String folded = TextFold.fold(text).replaceFirst("^[^\\p{L}\\p{N}]+", "");
        return rule.alternatives().stream().filter(folded::startsWith).findFirst();
    }

    /** The text as written starts with an alternative — status markers can be punctuation ("???"). */
    private static Optional<String> prefix(ProfileRule rule, String text) {
        String folded = TextFold.fold(text);
        return rule.alternatives().stream().filter(folded::startsWith).findFirst();
    }

    private static Optional<String> contains(ProfileRule rule, String text) {
        String folded = TextFold.fold(text);
        return rule.alternatives().stream().filter(folded::contains).findFirst();
    }

    /** {@code label:} at the start of a word; spaces inside the label are optional ("Čas predbežne" / "Časpredbežne"). */
    private static Pattern labelPattern(String label) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + spaced(label) + "\\s*:");
    }

    private static String spaced(String folded) {
        return Arrays.stream(folded.split(" ")).map(Pattern::quote).collect(Collectors.joining("\\s*"));
    }
}
