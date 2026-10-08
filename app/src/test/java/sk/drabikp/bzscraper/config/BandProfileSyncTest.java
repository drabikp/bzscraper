package sk.drabikp.bzscraper.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.bzscraper.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.model.RuleKind;
import sk.drabikp.bzscraper.domain.model.RuleOrigin;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BandProfileSyncTest {

    @Autowired
    private BandProfileStore profiles;

    @Test
    void the_shipped_rules_are_stored_on_start() {
        assertThat(profiles.rules()).filteredOn(r -> r.origin() == RuleOrigin.PRESET)
                .extracting(ProfileRule::kind)
                .contains(RuleKind.REPEATING, RuleKind.TITLE_STARTS_WITH, RuleKind.NOTES_LABEL);
    }
}
