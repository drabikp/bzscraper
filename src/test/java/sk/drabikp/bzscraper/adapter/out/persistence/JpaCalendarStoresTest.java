package sk.drabikp.bzscraper.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.model.RuleKind;
import sk.drabikp.bzscraper.domain.model.RuleOrigin;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JpaCalendarStoresTest {

    @Autowired
    private JpaBandProfileStore profiles;

    @Autowired
    private JpaCalendarDecisionStore decisions;

    private static ProfileRule user(RuleKind kind, String value, int weight) {
        return new ProfileRule(kind, value, weight, RuleOrigin.USER, true);
    }

    @Test
    void the_shipped_rules_are_stored_on_start() {
        assertThat(profiles.rules()).filteredOn(r -> r.origin() == RuleOrigin.PRESET)
                .extracting(ProfileRule::kind)
                .contains(RuleKind.REPEATING, RuleKind.TITLE_STARTS_WITH, RuleKind.NOTES_LABEL);
    }

    @Test
    void replacing_one_origin_keeps_the_others_and_the_order() {
        List<ProfileRule> presets = profiles.rules().stream().filter(r -> r.origin() == RuleOrigin.PRESET).toList();

        profiles.replace(RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "jana", -4), user(RuleKind.REPEATING, null, -8)));
        profiles.replace(RuleOrigin.USER, List.of(user(RuleKind.MEMBER, "jana|janka", -4),
                new ProfileRule(RuleKind.TITLE_CONTAINS, "fest", 2, RuleOrigin.USER, false)));

        assertThat(profiles.rules()).filteredOn(r -> r.origin() == RuleOrigin.USER).containsExactly(
                user(RuleKind.MEMBER, "jana|janka", -4),
                new ProfileRule(RuleKind.TITLE_CONTAINS, "fest", 2, RuleOrigin.USER, false));
        assertThat(profiles.rules()).filteredOn(r -> r.origin() == RuleOrigin.PRESET).isEqualTo(presets);
    }

    @Test
    void rules_of_another_origin_are_refused() {
        assertThatThrownBy(() -> profiles.replace(RuleOrigin.LEARNED, List.of(user(RuleKind.MEMBER, "jana", -4))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remembers_changes_and_forgets_verdicts() {
        decisions.decide("uid-1", CalendarEventKind.GIG);
        decisions.decide("uid-2", CalendarEventKind.NOT_GIG);
        decisions.decide("uid-1", CalendarEventKind.NOT_GIG);
        decisions.forget("uid-2");
        decisions.forget("never-decided");

        assertThat(decisions.all()).containsExactly(Map.entry("uid-1", CalendarEventKind.NOT_GIG));
    }
}
