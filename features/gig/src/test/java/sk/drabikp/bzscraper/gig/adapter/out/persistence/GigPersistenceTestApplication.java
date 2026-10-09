package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import sk.drabikp.bzscraper.gig.config.BackupProperties;

import java.time.Clock;

/**
 * The kernel's persistence on its own for its tests: JPA, the Flyway migrations on an
 * in-memory H2 and this package's beans — no UI, platforms or wiring of the app.
 */
@SpringBootApplication
// A test application of another module is on the classpath of the modules that use its test-jar: packaged,
// the jar leaves it out; in a reactor build (./mvnw test) its test-classes folder is used whole. @TestComponent
// keeps the other modules' context scans from picking it up there.
@TestComponent
@EnableConfigurationProperties(BackupProperties.class)
class GigPersistenceTestApplication {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
