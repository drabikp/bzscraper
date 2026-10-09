package sk.drabikp.bzscraper.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link LiveUpdates} as server-sent events: every open page holds one stream ({@link #open()});
 * a change is sent to all of them as an event named after its topic. Changes come in bursts (a
 * sync batch, an import), so a topic is sent once per {@value #COALESCE_MS} ms at most; a
 * comment every {@value #HEARTBEAT_S} s keeps idle streams open through proxies.
 */
@Component
class SseLiveUpdates implements LiveUpdates, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseLiveUpdates.class);
    static final long COALESCE_MS = 300;
    static final long HEARTBEAT_S = 25;

    private final List<SseEmitter> streams = new CopyOnWriteArrayList<>();
    private final Set<Topic> due = EnumSet.noneOf(Topic.class);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "live-updates");
        thread.setDaemon(true);
        return thread;
    });

    SseLiveUpdates() {
        timer.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_S, HEARTBEAT_S, TimeUnit.SECONDS);
    }

    /** A new stream for a page; it ends when the page goes away (or the server stops). */
    SseEmitter open() {
        SseEmitter stream = new SseEmitter(0L);
        streams.add(stream);
        stream.onCompletion(() -> streams.remove(stream));
        stream.onTimeout(() -> streams.remove(stream));
        stream.onError(e -> streams.remove(stream));
        send(stream, SseEmitter.event().name("hello").data("live"));
        return stream;
    }

    @Override
    public void changed(Topic topic) {
        synchronized (due) {
            if (!due.add(topic)) {
                return;
            }
        }
        timer.schedule(() -> {
            synchronized (due) {
                due.remove(topic);
            }
            String name = topic.name().toLowerCase();
            streams.forEach(stream -> send(stream, SseEmitter.event().name(name).data(name)));
        }, COALESCE_MS, TimeUnit.MILLISECONDS);
    }

    int streams() {
        return streams.size();
    }

    private void heartbeat() {
        streams.forEach(stream -> send(stream, SseEmitter.event().comment("still here")));
    }

    private void send(SseEmitter stream, SseEmitter.SseEventBuilder event) {
        try {
            stream.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("A page's live stream is gone", e);
            streams.remove(stream);
            stream.completeWithError(e);
        }
    }

    @Override
    public void destroy() {
        timer.shutdownNow();
        streams.forEach(SseEmitter::complete);
    }
}
