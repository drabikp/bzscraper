package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.domain.DateRange;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.util.List;

/** Reads gigs from the local catalog. */
public interface ListGigsUseCase {

    List<Gig> allGigs();

    List<Gig> gigsStartingWithin(DateRange range);
}
