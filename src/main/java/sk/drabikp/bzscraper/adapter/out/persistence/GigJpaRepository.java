package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/** Spring Data JPA repository over {@link GigEntity}. Internal to the persistence adapter. */
interface GigJpaRepository extends JpaRepository<GigEntity, String> {

    List<GigEntity> findByStartDateTimeGreaterThanEqualAndStartDateTimeLessThan(
            LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
