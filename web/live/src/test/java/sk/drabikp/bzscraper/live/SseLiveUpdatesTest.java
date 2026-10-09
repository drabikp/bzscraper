package sk.drabikp.bzscraper.live;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;

import static org.assertj.core.api.Assertions.assertThat;

class SseLiveUpdatesTest {

    @Test
    void a_stream_is_kept_while_open_and_a_change_with_no_page_open_goes_nowhere() throws Exception {
        SseLiveUpdates live = new SseLiveUpdates();
        try {
            live.changed(LiveUpdates.Topic.SYNC);
            live.open();
            assertThat(live.streams()).isEqualTo(1);
            live.changed(LiveUpdates.Topic.GIGS);
            live.changed(LiveUpdates.Topic.GIGS);
            Thread.sleep(SseLiveUpdates.COALESCE_MS * 2);
        } finally {
            live.destroy();
        }
    }
}
