package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * The persistence adapter on its own for its tests: JPA, the Flyway migrations on an
 * in-memory H2 and this package's beans — no UI, platforms or wiring of the app.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class PersistenceTestApplication {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
