package sk.drabikp.bzscraper.adapter.out.bandsintown;

import java.util.List;
import java.util.Map;

/**
 * Reads the portal's own JSON responses, captured from the page (see
 * {@link SeleniumBitSession}). Upload replies look like
 * {@code {"status":"OK","payload":{"draft_events":[{"row":2,"id":109006286,...}]}}};
 * rows count from 2 because row 1 is the CSV header.
 */
final class BitResponses {

    private static final int FIRST_DATA_ROW = 2;

    private BitResponses() {
    }

    /** True for HTTP 200 with {@code "status":"OK"}. */
    static boolean ok(Map<String, Object> entry) {
        return httpStatus(entry) == 200 && json(entry) != null && "OK".equals(json(entry).get("status"));
    }

    /** The reply's HTTP status, or -1 when it failed without one. */
    static int httpStatus(Map<String, Object> entry) {
        return entry != null ? (int) number(entry.get("status")) : -1;
    }

    /** Event ids by gig index for new (draft) events; null where a row got no id. */
    static String[] draftIds(Map<String, Object> entry, int gigCount) {
        return idsByRow(listOf(payload(entry), "draft_events"), gigCount);
    }

    /** Event ids by gig index for rows that updated an existing event. */
    static String[] updatedIds(Map<String, Object> entry, int gigCount) {
        return idsByRow(listOf(payload(entry), "updated_events"), gigCount);
    }

    /** The event list ({@code GET …/events?past=false}) payload. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> events(Map<String, Object> entry) {
        Map<String, Object> json = json(entry);
        Object payload = json != null ? json.get("payload") : null;
        return payload instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    /** The {@code payload.status} of a single-event reply (e.g. "DELETED"). */
    static String eventStatus(Map<String, Object> entry) {
        Map<String, Object> payload = payload(entry);
        return payload != null ? String.valueOf(payload.get("status")) : null;
    }

    /** Short text of a reply, for error messages. */
    static String describe(Map<String, Object> entry) {
        if (entry == null) {
            return "no reply";
        }
        String text = String.valueOf(entry.get("text"));
        return "HTTP " + entry.get("status") + ": " + (text.length() > 300 ? text.substring(0, 300) + "…" : text);
    }

    private static String[] idsByRow(List<Map<String, Object>> rows, int gigCount) {
        String[] ids = new String[gigCount];
        for (Map<String, Object> row : rows) {
            int index = (int) number(row.get("row")) - FIRST_DATA_ROW;
            Object id = row.get("id");
            if (index >= 0 && index < gigCount && id != null) {
                ids[index] = id instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(id);
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(Map<String, Object> entry) {
        return entry != null && entry.get("json") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> entry) {
        Map<String, Object> json = json(entry);
        return json != null && json.get("payload") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : -1;
    }
}
