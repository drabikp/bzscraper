package sk.drabikp.bzscraper.domain.service;

import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.util.List;

public final class GigDateFilter {
    private GigDateFilter() {
    }

    public static List<GigSummary> filter(List<GigSummary> gigs, DateRange range) {
        return gigs.stream()
                .filter(gig -> gig.start() != null)
                .filter(gig -> range.containsInclusive(gig.start().toLocalDate()))
                .toList();
    }
}
