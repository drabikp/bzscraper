package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link PublishedGigEntity}; used only by {@link JpaPublishedGigStore}. */
interface PublishedGigJpaRepository extends JpaRepository<PublishedGigEntity, PublishedGigEntity.Key> {

    List<PublishedGigEntity> findByKeyGigId(String gigId);
}
