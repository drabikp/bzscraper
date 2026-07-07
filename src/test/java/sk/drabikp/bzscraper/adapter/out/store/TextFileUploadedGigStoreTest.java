package sk.drabikp.bzscraper.adapter.out.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextFileUploadedGigStoreTest {

    @TempDir
    Path tmp;

    private TextFileUploadedGigStore store() {
        return new TextFileUploadedGigStore(tmp.resolve("uploaded-gigs.tsv").toString());
    }

    private static GigId key(int y, int m, int d, String venue) {
        return new GigId(LocalDate.of(y, m, d), venue);
    }

    @Test
    void unknown_gig_is_not_uploaded() {
        assertThat(store().isUploaded(Platform.BANDSINTOWN, key(2026, 9, 15, "klub 007"))).isFalse();
    }

    @Test
    void marked_gig_becomes_uploaded() {
        TextFileUploadedGigStore store = store();
        GigId k = key(2026, 9, 15, "klub 007");

        store.markUploaded(Platform.BANDSINTOWN, List.of(k));

        assertThat(store.isUploaded(Platform.BANDSINTOWN, k)).isTrue();
    }

    @Test
    void state_survives_a_new_instance_on_the_same_file() {
        GigId k = key(2026, 9, 15, "klub 007");
        store().markUploaded(Platform.BANDSINTOWN, List.of(k));

        // Fresh instance, same file → must see the earlier mark.
        assertThat(store().isUploaded(Platform.BANDSINTOWN, k)).isTrue();
    }

    @Test
    void platforms_are_isolated() {
        GigId k = key(2026, 9, 15, "klub 007");
        store().markUploaded(Platform.BANDSINTOWN, List.of(k));

        assertThat(store().isUploaded(Platform.BANDZONE, k)).isFalse();
    }

    @Test
    void marking_the_same_key_twice_does_not_duplicate_and_stays_uploaded() {
        TextFileUploadedGigStore store = store();
        GigId k = key(2026, 9, 15, "klub 007");

        store.markUploaded(Platform.BANDSINTOWN, List.of(k));
        store.markUploaded(Platform.BANDSINTOWN, List.of(k));

        assertThat(store.isUploaded(Platform.BANDSINTOWN, k)).isTrue();
    }
}
