package sk.drabikp.bzscraper.fetcher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
@Component
public class BZDataFetcher {
    private static final String BASE_URL = "https://bandzone.cz";
    private static final Logger logger = LoggerFactory.getLogger(BZDataFetcher.class);

    public Elements fetchAllGigs(String bandSlug) {
        Document document;
        try {
            String uriString = UriComponentsBuilder.fromHttpUrl(BASE_URL).pathSegment(bandSlug).queryParam("at", "gig").queryParam("gy", "0").build().toUriString();

            document = Jsoup.connect(uriString).get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> years = parseYears(document);
        Elements articles = new Elements();
        years.forEach(year -> {
            articles.addAll(getArticlesForYear(bandSlug, year));
        });

        return articles;
    }
    private List<String> parseYears(Document document) {
        List<String> years = document.select("div.years").first().select("span:not(.years-upcoming) a").stream().map(Element::text).toList();
        logger.debug("Parsed years: " + years);
        return years;
    }

    private Elements getArticlesForYear(String bandSlug, String year) {
        try {
            logger.debug("Downloading data for year " + year);
            String uriString = UriComponentsBuilder.fromHttpUrl(BASE_URL).pathSegment(bandSlug).queryParam("at", "gig").queryParam("gy", year).build().toUriString();
            return Jsoup.connect(uriString).get().select("article.gig");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
