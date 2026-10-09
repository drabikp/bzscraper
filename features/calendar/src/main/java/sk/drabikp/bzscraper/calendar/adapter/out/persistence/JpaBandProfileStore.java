package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;

import java.util.List;

/** JPA-backed {@link BandProfileStore}: rules in table {@code calendar_rule}, in insertion order. */
@Component
@Transactional
class JpaBandProfileStore implements BandProfileStore {

    private final CalendarRuleJpaRepository jpaRepository;

    public JpaBandProfileStore(CalendarRuleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileRule> rules() {
        return jpaRepository.findAllByOrderByIdAsc().stream().map(CalendarRuleEntity::toRule).toList();
    }

    @Override
    public void replace(RuleOrigin origin, List<ProfileRule> rules) {
        if (rules.stream().anyMatch(r -> r.origin() != origin)) {
            throw new IllegalArgumentException("every rule must have origin " + origin);
        }
        jpaRepository.deleteByOrigin(origin.name());
        jpaRepository.saveAll(rules.stream().map(CalendarRuleEntity::of).toList());
    }
}
