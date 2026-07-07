package sk.drabikp.bzscraper.adapter.out.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TextFilePublishedGigStoreTest {

    @TempDir
    Path tmp;

    private TextFilePublishedGigStore store() {
        return new TextFilePublishedGigStore(tmp.resolve("published-gigs.tsv").toString());
    }

    private static GigId key(String venue) {
        return new GigId(LocalDate.of(2026, 9, 15), venue);
    }

    @Test
    void unknown_gig_is_not_published_and_has_no_ref() {
        TextFilePublishedGigStore store = store();
        assertThat(store.isPublished(Platform.BANDZONE, key("klub 007"))).isFalse();
        assertThat(store.externalRef(Platform.BANDZONE, key("klub 007"))).isEmpty();
    }

    @Test
    void recording_with_a_ref_makes_it_published_and_exposes_the_ref() {
        TextFilePublishedGigStore store = store();
        GigId k = key("klub 007");

        store.record(Platform.BANDZONE, k, "561859");

        assertThat(store.isPublished(Platform.BANDZONE, k)).isTrue();
        assertThat(store.externalRef(Platform.BANDZONE, k)).contains("561859");
    }

    @Test
    void recording_without_a_ref_still_marks_published_but_ref_is_empty() {
        TextFilePublishedGigStore store = store();
        GigId k = key("klub 007");

        store.record(Platform.BANDSINTOWN, k, null);

        assertThat(store.isPublished(Platform.BANDSINTOWN, k)).isTrue();
        assertThat(store.externalRef(Platform.BANDSINTOWN, k)).isEmpty();
    }

    @Test
    void state_and_ref_survive_a_new_instance_on_the_same_file() {
        store().record(Platform.BANDZONE, key("klub 007"), "561859");

        assertThat(store().externalRef(Platform.BANDZONE, key("klub 007"))).contains("561859");
    }

    @Test
    void recording_again_updates_the_ref() {
        TextFilePublishedGigStore store = store();
        GigId k = key("klub 007");

        store.record(Platform.BANDZONE, k, "old");
        store.record(Platform.BANDZONE, k, "new");

        assertThat(store.externalRef(Platform.BANDZONE, k)).contains("new");
    }

    @Test
    void remove_forgets_the_gig() {
        TextFilePublishedGigStore store = store();
        GigId k = key("klub 007");
        store.record(Platform.BANDZONE, k, "561859");

        store.remove(Platform.BANDZONE, k);

        assertThat(store.isPublished(Platform.BANDZONE, k)).isFalse();
    }

    @Test
    void platforms_are_isolated() {
        TextFilePublishedGigStore store = store();
        GigId k = key("klub 007");
        store.record(Platform.BANDSINTOWN, k, "evt");

        assertThat(store.isPublished(Platform.BANDZONE, k)).isFalse();
    }
}
