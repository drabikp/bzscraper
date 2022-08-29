package sk.drabikp.bzscraper.service;

import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sk.drabikp.bzscraper.fetcher.BZDataFetcher;
import sk.drabikp.bzscraper.gigsummary.GigSummary;
import sk.drabikp.bzscraper.gigsummary.GigSummaryParser;

import java.util.List;

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
}
