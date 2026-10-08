package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed {@link GigRepository}. Wraps the Spring Data repository and the
 * {@link GigEntityMapper}, keeping all persistence concerns out of the domain and
 * application layers. {@code save} upserts by gig identity (assigned id).
 */
@Component
@Transactional
public class JpaGigRepository implements GigRepository {

    private final GigJpaRepository jpaRepository;

    public JpaGigRepository(GigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Gig gig) {
        GigEntity entity = GigEntityMapper.toEntity(gig);
        // an update carries the version read in this transaction: if another transaction
        // saved the gig meanwhile, the commit fails instead of overwriting its change
        jpaRepository.findById(entity.getId()).ifPresent(existing -> entity.setVersion(existing.getVersion()));
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Gig> findById(GigId id) {
        return jpaRepository.findById(GigEntityMapper.serializeId(id)).map(GigEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Gig> findAll() {
        return jpaRepository.findAll().stream().map(GigEntityMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Gig> findStartingWithin(DateRange range) {
        LocalDateTime fromInclusive = range.start().atStartOfDay();
        LocalDateTime toExclusive = range.end().plusDays(1).atStartOfDay();
        return jpaRepository
                .findByStartDateTimeGreaterThanEqualAndStartDateTimeLessThan(fromInclusive, toExclusive)
                .stream().map(GigEntityMapper::toDomain).toList();
    }

    @Override
    public void deleteById(GigId id) {
        jpaRepository.deleteById(GigEntityMapper.serializeId(id));
    }
}
