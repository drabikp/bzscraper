package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link PublishedGigEntity}; used only by {@link JpaPublishedGigStore}. */
public interface PublishedGigJpaRepository extends JpaRepository<PublishedGigEntity, PublishedGigEntity.Key> {

    List<PublishedGigEntity> findByKeyGigId(String gigId);
}
