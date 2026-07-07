package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.GigProvider;
import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.util.List;

@Component
public class BandzoneGigProvider implements GigProvider {

    private final BandzoneHtmlFetcher fetcher = new BandzoneHtmlFetcher();
    private final BandzoneHtmlParser parser = new BandzoneHtmlParser();

    @Override
    public List<GigSummary> findByBand(String bandSlug) {
        return parser.parse(fetcher.fetchAllGigs(bandSlug));
    }
}
