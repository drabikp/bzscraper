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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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

        // this performs better than a parallel stream
        ExecutorService executorService = Executors.newFixedThreadPool(years.size());
        List<Future<Elements>> futureList = new ArrayList<>(years.size());
        years.forEach(year -> {
            futureList.add(executorService.submit(() -> getArticlesForYear(bandSlug, year)));
            logger.debug("[{}] - submitted task for year {}", bandSlug, year);
        });

        executorService.shutdown();
        try {
            //noinspection ResultOfMethodCallIgnored
            executorService.awaitTermination(60L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.debug("[{}] - executorService shutdown called", bandSlug);

        futureList.forEach(elementsFuture -> {
            try {
                articles.addAll(elementsFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
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
            logger.debug("Downloading data for year {}", year);
            String uriString = UriComponentsBuilder.fromHttpUrl(BASE_URL).pathSegment(bandSlug).queryParam("at", "gig").queryParam("gy", year).build().toUriString();
            Elements elements = Jsoup.connect(uriString).get().select("article.gig");
            logger.debug("Data for year {} downloaded", year);
            return elements;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
