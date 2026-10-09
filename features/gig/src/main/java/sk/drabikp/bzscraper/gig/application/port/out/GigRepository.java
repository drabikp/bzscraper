package sk.drabikp.bzscraper.gig.application.port.out;

import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the local gig store — the source of truth. Speaks only the
 * domain ({@link Gig} / {@link GigId}); the persistence technology (JPA/H2) lives
 * entirely behind the implementing adapter, so no framework annotation touches the
 * domain. {@code save} is an upsert keyed by {@link GigId}.
 */
public interface GigRepository {

    void save(Gig gig);

    Optional<Gig> findById(GigId id);

    List<Gig> findAll();

    List<Gig> findStartingWithin(DateRange range);

    void deleteById(GigId id);
}
