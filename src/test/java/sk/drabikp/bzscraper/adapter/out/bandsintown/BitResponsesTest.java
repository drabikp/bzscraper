package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Replies as the artist portal sent them during the live capture (2026-10-07). */
class BitResponsesTest {

    private static Map<String, Object> reply(long status, Map<String, Object> json) {
        return Map.of("method", "PATCH", "url", "/api/…", "status", status, "json", json, "text", "…");
    }

    @Test
    void maps_draft_rows_to_gig_indexes() {
        Map<String, Object> entry = reply(200, Map.of("status", "OK", "payload", Map.of("draft_events", List.of(
                Map.of("row", 2L, "id", 109006286L, "status", "DRAFT"),
                Map.of("row", 4L, "id", 109006288L, "status", "DRAFT")))));

        assertThat(BitResponses.ok(entry)).isTrue();
        assertThat(BitResponses.draftIds(entry, 3)).containsExactly("109006286", null, "109006288");
        assertThat(BitResponses.updatedIds(entry, 3)).containsExactly(null, null, null);
    }

    @Test
    void reads_updated_rows() {
        Map<String, Object> entry = reply(200, Map.of("status", "OK", "payload", Map.of("updated_events",
                List.of(Map.of("row", 2L, "id", 109006286L, "status", "DRAFT")))));

        assertThat(BitResponses.updatedIds(entry, 1)).containsExactly("109006286");
        assertThat(BitResponses.draftIds(entry, 1)).containsExactly((String) null);
    }

    @Test
    void reads_a_single_event_status_and_the_event_list() {
        assertThat(BitResponses.eventStatus(reply(200, Map.of("status", "OK",
                "payload", Map.of("id", 109006286L, "status", "DELETED"))))).isEqualTo("DELETED");
        assertThat(BitResponses.events(reply(200, Map.of("status", "OK",
                "payload", List.of(Map.of("id", 1L)))))).hasSize(1);
    }

    @Test
    void an_error_reply_is_not_ok_and_is_described() {
        Map<String, Object> entry = reply(401, Map.of("status", "ERROR"));

        assertThat(BitResponses.ok(entry)).isFalse();
        assertThat(BitResponses.httpStatus(entry)).isEqualTo(401);
        assertThat(BitResponses.describe(entry)).startsWith("HTTP 401");
        assertThat(BitResponses.ok(null)).isFalse();
    }

    @Test
    void reads_the_row_errors_bandsintown_refused_an_upload_with() {
        // live reply (2026-10-08) to an edit of an event in the past
        Map<String, Object> refused = reply(200, Map.of("status", "ERROR",
                "error", Map.of("message", List.of(Map.of("row", 2L, "error", "INVALID_START_TIME")))));

        assertThat(BitResponses.ok(refused)).isFalse();
        assertThat(BitResponses.rowErrors(refused)).containsExactly("INVALID_START_TIME");
        assertThat(BitResponses.rowErrors(reply(401, Map.of("status", "ERROR")))).isEmpty();
        assertThat(BitResponses.rowErrors(null)).isEmpty();
    }
}
