package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.util.List;

public interface GetGigsBetweenDatesUseCase {
    List<GigSummary> byBandBetween(String bandSlug, DateRange range);
}
