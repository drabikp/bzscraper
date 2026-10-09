package sk.drabikp.bzscraper.calendar.application.port.out;

import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;

import java.util.List;

/** The band profile's rules. Each origin is replaced as a whole by whoever owns it. */
public interface BandProfileStore {

    List<ProfileRule> rules();

    /** Replaces every rule of {@code origin} with {@code rules} (all of that origin). */
    void replace(RuleOrigin origin, List<ProfileRule> rules);
}
