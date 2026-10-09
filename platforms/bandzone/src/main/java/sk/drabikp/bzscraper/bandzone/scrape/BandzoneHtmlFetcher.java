package sk.drabikp.bzscraper.bandzone.scrape;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;
import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class BandzoneHtmlFetcher {
    private static final String BASE_URL = "https://bandzone.cz";
    /** Pseudo-year for the band's "Plánované koncerty" (planned gigs) tab. */
    private static final String UPCOMING = "upcoming";
    private static final Logger logger = LoggerFactory.getLogger(BandzoneHtmlFetcher.class);

    Elements fetchAllGigs(String bandSlug) throws BandzoneUploadException {
        Document document;
        try {
            String uriString = UriComponentsBuilder.fromUriString(BASE_URL).pathSegment(bandSlug).queryParam("at", "gig").queryParam("gy", "0").build().toUriString();
            document = Jsoup.connect(uriString).get();
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                throw BandzoneUploadException.needsUser("Bandzone has no band '" + bandSlug
                        + "' — check bzscraper.bandzone.band-slug.");
            }
            throw new BandzoneUploadException("Bandzone answered " + e.getStatusCode() + " for the band page.", e);
        } catch (IOException e) {
            throw new BandzoneUploadException("Could not read Bandzone: " + e.getMessage(), e);
        }

        List<String> years = parseYears(document);
        Elements articles = new Elements();

        // the year tabs list played gigs only; planned ones are on the separate "upcoming" tab
        List<String> pages = new ArrayList<>(years);
        pages.add(UPCOMING);
        ExecutorService executorService = Executors.newFixedThreadPool(pages.size());
        List<Future<Elements>> futureList = new ArrayList<>(pages.size());
        pages.forEach(year -> {
            futureList.add(executorService.submit(() -> getArticlesForYear(bandSlug, year)));
            logger.debug("[{}] - submitted task for year {}", bandSlug, year);
        });

        executorService.shutdown();
        try {
            //noinspection ResultOfMethodCallIgnored
            executorService.awaitTermination(60L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BandzoneUploadException("Interrupted while reading Bandzone.", e);
        }
        logger.debug("[{}] - executorService shutdown called", bandSlug);

        for (Future<Elements> elementsFuture : futureList) {
            try {
                articles.addAll(elementsFuture.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BandzoneUploadException("Interrupted while reading Bandzone.", e);
            } catch (ExecutionException e) {
                throw new BandzoneUploadException("Could not read a year of Bandzone gigs: "
                        + e.getCause().getMessage(), e.getCause());
            }
        }

        return articles;
    }

    private List<String> parseYears(Document document) {
        Element tabs = document.selectFirst("div.years");
        List<String> years = tabs == null ? List.of()
                : tabs.select("span:not(.years-upcoming) a").stream().map(Element::text).toList();
        logger.debug("Parsed years: {}", years);
        return years;
    }

    private Elements getArticlesForYear(String bandSlug, String year) throws IOException {
        logger.debug("Downloading data for year {}", year);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(BASE_URL).pathSegment(bandSlug).queryParam("at", "gig");
        String uriString = (UPCOMING.equals(year) ? uri : uri.queryParam("gy", year)).build().toUriString();
        Elements elements = Jsoup.connect(uriString).get().select("article.gig");
        logger.debug("Data for year {} downloaded", year);
        return elements;
    }
}
