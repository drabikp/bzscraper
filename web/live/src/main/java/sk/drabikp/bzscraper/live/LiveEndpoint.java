package sk.drabikp.bzscraper.live;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /api/events}: the page's stream of live updates. Events are named after what
 * changed ({@code gigs}, {@code sync}, {@code check}, {@code import}, {@code calendar}); the page
 * reads that part of the app again through the API.
 */
@RestController
class LiveEndpoint {

    private final SseLiveUpdates live;

    LiveEndpoint(SseLiveUpdates live) {
        this.live = live;
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events() {
        return live.open();
    }
}
