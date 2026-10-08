package sk.drabikp.bzscraper.adapter.out.persistence;

import sk.drabikp.bzscraper.TestPlatforms;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.TestGigs;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaPublishedGigStoreTest {

    @Autowired
    private JpaPublishedGigStore store;

    private final GigId klub = TestGigs.gig("A", "Klub 007").id();
    private final GigId barrak = TestGigs.gig("A", "Barrák").id();

    @Test
    void records_and_reads_back_a_published_gig_per_platform() {
        store.record(TestPlatforms.BANDZONE, klub, "100");

        assertThat(store.isPublished(TestPlatforms.BANDZONE, klub)).isTrue();
        assertThat(store.externalRef(TestPlatforms.BANDZONE, klub)).contains("100");
        assertThat(store.isPublished(TestPlatforms.BANDSINTOWN, klub)).isFalse();
        assertThat(store.isPublished(TestPlatforms.BANDZONE, barrak)).isFalse();
    }

    @Test
    void a_publish_without_an_external_id_is_published_but_has_no_ref() {
        store.record(TestPlatforms.BANDSINTOWN, klub, null);

        assertThat(store.isPublished(TestPlatforms.BANDSINTOWN, klub)).isTrue();
        assertThat(store.externalRef(TestPlatforms.BANDSINTOWN, klub)).isEmpty();
    }

    @Test
    void recording_again_replaces_the_ref() {
        store.record(TestPlatforms.BANDZONE, klub, "100");
        store.record(TestPlatforms.BANDZONE, klub, "200");

        assertThat(store.externalRef(TestPlatforms.BANDZONE, klub)).contains("200");
    }

    @Test
    void remove_forgets_the_gig_on_that_platform_only() {
        store.record(TestPlatforms.BANDZONE, klub, "100");
        store.record(TestPlatforms.BANDSINTOWN, klub, null);

        store.remove(TestPlatforms.BANDZONE, klub);

        assertThat(store.isPublished(TestPlatforms.BANDZONE, klub)).isFalse();
        assertThat(store.isPublished(TestPlatforms.BANDSINTOWN, klub)).isTrue();
    }

    @Test
    void move_re_keys_every_platform_record_and_keeps_the_refs() {
        store.record(TestPlatforms.BANDZONE, klub, "100");
        store.record(TestPlatforms.BANDSINTOWN, klub, null);

        store.move(klub, barrak);

        assertThat(store.isPublished(TestPlatforms.BANDZONE, klub)).isFalse();
        assertThat(store.isPublished(TestPlatforms.BANDSINTOWN, klub)).isFalse();
        assertThat(store.externalRef(TestPlatforms.BANDZONE, barrak)).contains("100");
        assertThat(store.isPublished(TestPlatforms.BANDSINTOWN, barrak)).isTrue();
    }

    @Test
    void move_of_an_unpublished_gig_or_to_the_same_id_changes_nothing() {
        store.record(TestPlatforms.BANDZONE, klub, "100");

        store.move(barrak, klub);
        store.move(klub, klub);

        assertThat(store.externalRef(TestPlatforms.BANDZONE, klub)).contains("100");
        assertThat(store.isPublished(TestPlatforms.BANDZONE, barrak)).isFalse();
    }

    @Test
    void lists_every_publication_with_its_gig_identity() {
        store.record(TestPlatforms.BANDZONE, klub, "100");
        store.record(TestPlatforms.BANDSINTOWN, barrak, null);

        assertThat(store.all()).containsExactlyInAnyOrder(
                new Publication(TestPlatforms.BANDZONE, klub, "100"),
                new Publication(TestPlatforms.BANDSINTOWN, barrak, null));
    }
}
