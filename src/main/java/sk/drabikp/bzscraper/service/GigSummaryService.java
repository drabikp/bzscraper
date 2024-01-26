package sk.drabikp.bzscraper.service;

import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sk.drabikp.bzscraper.fetcher.BZDataFetcher;
import sk.drabikp.bzscraper.gigsummary.GigSummary;
import sk.drabikp.bzscraper.gigsummary.GigSummaryDto;
import sk.drabikp.bzscraper.gigsummary.GigSummaryParser;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GigSummaryService {
    private final GigSummaryParser gigSummaryParser;
    private final BZDataFetcher bzDataFetcher;

    @Autowired
    public GigSummaryService(GigSummaryParser gigSummaryParser, BZDataFetcher bzDataFetcher) {
        this.gigSummaryParser = gigSummaryParser;
        this.bzDataFetcher = bzDataFetcher;
    }

    public List<GigSummary> getAllGigsForBand(String bandSlug) {
        Elements elements = bzDataFetcher.fetchAllGigs(bandSlug);
        return gigSummaryParser.parse(elements);
    }

    public GigSummaryDto getGigsBetweenDates(String bandSlug, LocalDate startDate, LocalDate endDate) {
        ZoneId zoneId = ZoneId.systemDefault();
        List<GigSummary> allGigs = getAllGigsForBand(bandSlug);
        List<GigSummary> filteredGigs = allGigs.stream()
                .filter(gig -> {
                    LocalDate gigDate = gig.start().toLocalDate();
                    return !gigDate.isBefore(startDate) && !gigDate.isAfter(endDate);
                })
                .collect(Collectors.toList());

        return new GigSummaryDto(filteredGigs);
    }
}
