package sk.drabikp.bzscraper.bandsintown;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;

/**
 * Bandsintown as the core knows it: no cancelled state (cancelling removes the event), an
 * event is when the artist plays (the band's slot of a longer event), no entry information.
 */
@Configuration
public class BandsintownPlatform {

    public static final Platform PLATFORM = Platform.of("BANDSINTOWN");

    @Bean
    PlatformTraits bandsintownTraits() {
        return new PlatformTraits(PLATFORM, "Bandsintown", false, true, false, 20,
                "https://www.bandsintown.com/e/{id}");
    }
}
