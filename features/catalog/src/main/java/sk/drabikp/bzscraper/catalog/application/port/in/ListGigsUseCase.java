package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;

import java.util.List;

/** Reads gigs from the local catalog. */
public interface ListGigsUseCase {

    List<Gig> allGigs();

    /** The gig; {@link sk.drabikp.bzscraper.gig.application.NotFoundException} when it isn't (any more). */
    Gig gig(GigId id);

    List<Gig> gigsStartingWithin(DateRange range);
}
