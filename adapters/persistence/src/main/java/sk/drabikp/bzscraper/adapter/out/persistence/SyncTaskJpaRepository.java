package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link SyncTaskEntity}; used only by {@link JpaSyncOutbox}. */
public interface SyncTaskJpaRepository extends JpaRepository<SyncTaskEntity, Long> {

    List<SyncTaskEntity> findByGigIdAndStatusInOrderByIdAsc(String gigId, Collection<String> statuses);

    List<SyncTaskEntity> findByStatusInOrderByIdAsc(Collection<String> statuses);

    List<SyncTaskEntity> findByGigIdAndPlatformAndStatusOrderByIdAsc(String gigId, String platform, String status);

    List<SyncTaskEntity> findByGigId(String gigId);

    Optional<SyncTaskEntity> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(String status, Instant now);

    List<SyncTaskEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(String status, Instant now);

    List<SyncTaskEntity> findByPlatformAndActionAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            String platform, String action, String status, Instant now);

    List<SyncTaskEntity> findAllByOrderByIdDesc(Limit limit);
}
