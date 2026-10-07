package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed {@link PublishedGigStore}, in the same database as the gig catalog, so a
 * catalog change and the matching record change can commit together (see
 * {@link sk.drabikp.bzscraper.application.port.out.Transactions}).
 */
@Component
@Transactional
public class JpaPublishedGigStore implements PublishedGigStore {

    private final PublishedGigJpaRepository jpaRepository;

    public JpaPublishedGigStore(PublishedGigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPublished(Platform platform, GigId gigId) {
        return jpaRepository.existsById(key(platform, gigId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> externalRef(Platform platform, GigId gigId) {
        return jpaRepository.findById(key(platform, gigId))
                .map(PublishedGigEntity::getExternalRef)
                .filter(ref -> !ref.isBlank());
    }

    @Override
    public void record(Platform platform, GigId gigId, String externalRef) {
        jpaRepository.save(new PublishedGigEntity(key(platform, gigId), externalRef));
    }

    @Override
    public void remove(Platform platform, GigId gigId) {
        jpaRepository.deleteById(key(platform, gigId));
    }

    @Override
    public void move(GigId from, GigId to) {
        if (from.equals(to)) {
            return;
        }
        List<PublishedGigEntity> records = jpaRepository.findByKeyGigId(GigEntityMapper.serializeId(from));
        jpaRepository.deleteAll(records);
        jpaRepository.flush(); // delete before insert, in case the keys collide
        for (PublishedGigEntity old : records) {
            record(old.getKey().platform(), to, old.getExternalRef());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Publication> all() {
        return jpaRepository.findAll().stream()
                .map(e -> new Publication(e.getKey().platform(),
                        GigEntityMapper.deserializeId(e.getKey().gigId()), e.getExternalRef()))
                .toList();
    }

    private static PublishedGigEntity.Key key(Platform platform, GigId gigId) {
        return new PublishedGigEntity.Key(platform, GigEntityMapper.serializeId(gigId));
    }
}
