package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformTraits;

/**
 * Bandzone.cz as the core knows it: a cancelled concert stays listed as cancelled, a concert is
 * the whole event (not the band's slot), the entry is shown; its details win on import over
 * other platforms'.
 */
@Configuration
public class BandzonePlatform {

    public static final Platform PLATFORM = Platform.of("BANDZONE");

    @Bean
    PlatformTraits bandzoneTraits() {
        return new PlatformTraits(PLATFORM, "Bandzone", true, false, true, 10,
                "https://bandzone.cz/koncert/{id}");                // redirects to the full page
    }
}
