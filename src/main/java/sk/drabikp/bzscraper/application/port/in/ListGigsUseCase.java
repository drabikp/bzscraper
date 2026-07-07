package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.util.List;

/** Reads gigs from the local catalog. */
public interface ListGigsUseCase {

    List<Gig> allGigs();

    List<Gig> gigsStartingWithin(DateRange range);
}
