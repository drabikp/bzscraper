package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Spring Data repository for {@link CalendarRuleEntity}; used only by {@link JpaBandProfileStore}. */
public interface CalendarRuleJpaRepository extends JpaRepository<CalendarRuleEntity, Long> {

    List<CalendarRuleEntity> findAllByOrderByIdAsc();

    @Modifying
    @Query("delete from CalendarRuleEntity r where r.origin = :origin")
    void deleteByOrigin(String origin);
}
