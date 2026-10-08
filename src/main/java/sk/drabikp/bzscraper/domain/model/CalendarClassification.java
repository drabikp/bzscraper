package sk.drabikp.bzscraper.domain.model;

import java.util.List;

/**
 * What a calendar event is and why. {@code suggested} is what the band profile says;
 * {@code kind} is the final answer — the user's remembered verdict when there is one
 * ({@code decidedByUser}), otherwise the suggestion. {@code reasons} are the rules that
 * matched, with the weight each added to {@code score}.
 */
public record CalendarClassification(CalendarEvent event, CalendarEventKind kind, CalendarEventKind suggested,
                                     boolean decidedByUser, CalendarEventStatus status, int score,
                                     List<Reason> reasons) {

    /** One matched rule: a readable description and the weight it added (0 for status rules). */
    public record Reason(String text, int weight) {
    }

    public CalendarClassification {
        reasons = List.copyOf(reasons);
    }
}
