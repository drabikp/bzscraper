package sk.drabikp.bzscraper.calendar.adapter.out.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.config.CalendarProperties;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BandProfileSyncTest {

    /** The stored rules, replaced per origin. */
    private static final class Store implements BandProfileStore {

        private final List<ProfileRule> rules = new ArrayList<>();

        @Override
        public List<ProfileRule> rules() {
            return List.copyOf(rules);
        }

        @Override
        public void replace(RuleOrigin origin, List<ProfileRule> replacement) {
            rules.removeIf(r -> r.origin() == origin);
            rules.addAll(replacement);
        }
    }

    private final Store store = new Store();

    @TempDir
    Path dir;

    private static CalendarProperties properties(String profileFile) {
        return new CalendarProperties(null, null, "sk-cz", profileFile, null, null, null);
    }

    @Test
    void the_shipped_rules_are_stored_as_presets_on_start_and_the_bands_file_as_its_own() throws IOException {
        Path file = Files.writeString(dir.resolve("profile.txt"), "TITLE_STARTS_WITH -6 skuska|reh\n");

        new BandProfileSync(store, properties(file.toString())).run(null);

        assertThat(store.rules()).filteredOn(r -> r.origin() == RuleOrigin.PRESET).extracting(ProfileRule::kind)
                .contains(RuleKind.REPEATING, RuleKind.TITLE_STARTS_WITH, RuleKind.NOTES_LABEL);
        assertThat(store.rules()).filteredOn(r -> r.origin() == RuleOrigin.USER)
                .containsExactly(new ProfileRule(RuleKind.TITLE_STARTS_WITH, "skuska|reh", -6, RuleOrigin.USER, true));
    }

    @Test
    void a_broken_band_file_keeps_the_previous_band_rules() throws IOException {
        Path file = Files.writeString(dir.resolve("profile.txt"), "TITLE_STARTS_WITH -6 skuska\n");
        new BandProfileSync(store, properties(file.toString())).run(null);
        Files.writeString(file, "TITLE_START -6 skuska\n");

        new BandProfileSync(store, properties(file.toString())).run(null);

        assertThat(store.rules()).filteredOn(r -> r.origin() == RuleOrigin.USER).extracting(ProfileRule::value)
                .containsExactly("skuska");
    }
}
