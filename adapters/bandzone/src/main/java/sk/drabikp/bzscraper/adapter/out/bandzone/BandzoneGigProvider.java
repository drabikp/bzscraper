package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

@Component
public class BandzoneGigProvider implements GigProvider {

    private final BandzoneHtmlFetcher fetcher = new BandzoneHtmlFetcher();
    private final BandzoneHtmlParser parser;

    public BandzoneGigProvider(Clock clock) {
        this.parser = new BandzoneHtmlParser(clock);
    }

    @Override
    public List<GigSummary> findByBand(String bandSlug) throws BandzoneUploadException {
        return parser.parse(fetcher.fetchAllGigs(bandSlug));
    }
}
