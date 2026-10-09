package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;

/** JPA persistence model for one band-profile rule; kind and origin are stored by name. */
@Entity
@Table(name = "calendar_rule")
class CalendarRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String kind;
    private String ruleValue;
    private int weight;
    private String origin;
    private boolean enabled;

    protected CalendarRuleEntity() {
        // for JPA
    }

    static CalendarRuleEntity of(ProfileRule rule) {
        CalendarRuleEntity entity = new CalendarRuleEntity();
        entity.kind = rule.kind().name();
        entity.ruleValue = rule.value();
        entity.weight = rule.weight();
        entity.origin = rule.origin().name();
        entity.enabled = rule.enabled();
        return entity;
    }

    ProfileRule toRule() {
        return new ProfileRule(RuleKind.valueOf(kind), ruleValue, weight, RuleOrigin.valueOf(origin), enabled);
    }

    String getOrigin() {
        return origin;
    }
}
