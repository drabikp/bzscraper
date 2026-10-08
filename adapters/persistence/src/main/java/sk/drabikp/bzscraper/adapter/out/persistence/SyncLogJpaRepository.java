package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link SyncLogEntity}; used only by {@link JpaSyncOutbox}. */
public interface SyncLogJpaRepository extends JpaRepository<SyncLogEntity, Long> {

    List<SyncLogEntity> findByTaskIdOrderByIdAsc(Long taskId);
}
