package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.GetGigsBetweenDatesUseCase;
import sk.drabikp.bzscraper.application.port.in.GetGigsForBandUseCase;
import sk.drabikp.bzscraper.application.port.out.GigProvider;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.GigSummary;
import sk.drabikp.bzscraper.domain.service.GigDateFilter;

import java.util.List;

public class GigQueryService implements GetGigsForBandUseCase, GetGigsBetweenDatesUseCase {

    private final GigProvider gigProvider;

    public GigQueryService(GigProvider gigProvider) {
        this.gigProvider = gigProvider;
    }

    @Override
    public List<GigSummary> byBand(String bandSlug) {
        return gigProvider.findByBand(bandSlug);
    }

    @Override
    public List<GigSummary> byBandBetween(String bandSlug, DateRange range) {
        return GigDateFilter.filter(gigProvider.findByBand(bandSlug), range);
    }
}
