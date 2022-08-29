package sk.drabikp.bzscraper.gigsummary;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
@Component
public class GigSummaryParser {

    private static final Logger logger = LoggerFactory.getLogger(GigSummaryParser.class);

    public List<GigSummary> parse(Elements elements) {
        return elements.stream().map(this::parseGig).toList();
    }

    private GigSummary parseGig(Element element) {
        ZonedDateTime startDateTime = parseStartDateTimeFromArticle(element);
        ZonedDateTime endDateTime = parseEndDateTimeFromArticle(element);

        String city = parseCityFromArticle(element);
        String venue = parseVenueFromArticle(element);
        String url = parseUrlFromArticle(element);
        String bzId = parseBzId(url);
        List<String> bands = parseBandsFromArticle(element);
        String title = parseTitleFromArticle(element);
        String entryFee = parseEntryFeeFromArticle(element);
        boolean isCancelled = parseIsCancelled(element);

        return new GigSummary(bzId, title, startDateTime, endDateTime, url, city, venue, bands, entryFee, isCancelled);
    }

    private String parseBzId(String url) {
        // url usually looks like /koncert/527151-valca-dielec-eufory
        try {
            String urlPart = url.split("/")[2];
            return urlPart.substring(0, urlPart.indexOf("-"));
        } catch (Exception e) {
            logger.error("Cannot parse id from url {}", url);
            throw new RuntimeException(e);
        }
    }
    private static ZonedDateTime parseStartDateTimeFromArticle(Element article) {
        try {
            String dateTime = article.getElementsByAttributeValue("itemprop", "startDate").select("time:not(.is-hide)").first().attr("datetime");
            return ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME);
        } catch (NullPointerException e) {
            logger.debug("Cannot parse start date, returning null");
            return null;
        }
    }

    private static boolean parseIsCancelled(Element article) {
        return article.hasClass("gig--canceled");
    }

    private static ZonedDateTime parseEndDateTimeFromArticle(Element article) {
        try {
            String dateTime = article.getElementsByAttributeValue("itemprop", "endDate").select("time").first().attr("datetime");
            return ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME);
        } catch (NullPointerException e) {
            logger.debug("Cannot parse end date, returning null");
            return null;
        }
    }

    private static String parseTitleFromArticle(Element article) {
        try {
            return article.select("h3.gig__title").first().text();
        } catch (NullPointerException e) {
            logger.debug("Cannot parse title, returning null");
            return null;
        }
    }

    private static List<String> parseBandsFromArticle(Element article) {
        try {
            return article.select("div.gig__info__item--bands-list").first().select("h5.gig__bands__name").stream()
                    .map(Element::text)
                    .map(s -> s.charAt(s.length()-1) == ',' ? s.substring(0, s.length()-1): s)
                    .toList();
        } catch (NullPointerException e) {
            logger.debug("Cannot parse bands, returning null");
            return null;
        }
    }

    private static String parseEntryFeeFromArticle(Element article) {
        try {
            return article.select("div.gig__info__item--entry").first().attr("title");
        } catch (NullPointerException e) {
            logger.debug("Cannot parse entry fee, returning null");
            return null;
        }
    }

    private static String parseUrlFromArticle(Element article) {
        try {
            return article.select("a.gig__link").first().attr("href");
        } catch (NullPointerException e) {
            logger.debug("Cannot parse url, returning null");
            return null;
        }
    }

    private static String parseCityFromArticle(Element article) {
        try {
            return article.getElementsByAttributeValue("itemprop", "location").first().select("strong").first().attr("title");
        } catch (NullPointerException e) {
            logger.debug("Cannot parse city, returning null");
            return null;
        }
    }

    private static String parseVenueFromArticle(Element article) {
        try {
            return article.getElementsByAttributeValue("itemprop", "location").first().select("span").first().select("span").attr("title");
        } catch (NullPointerException e) {
            logger.debug("Cannot parse venue, returning null");
            return null;
        }
    }
}
