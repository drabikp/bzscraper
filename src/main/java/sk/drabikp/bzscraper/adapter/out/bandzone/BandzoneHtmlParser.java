package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

class BandzoneHtmlParser {

    private static final Logger logger = LoggerFactory.getLogger(BandzoneHtmlParser.class);

    List<GigSummary> parse(Elements elements) {
        return elements.stream().parallel().map(this::parseGig).sorted(Comparator.comparing(GigSummary::start)).toList();
    }

    private GigSummary parseGig(Element element) {
        String url = parseUrlFromArticle(element);
        ZonedDateTime start = parseStartDateTimeFromArticle(element);
        ZonedDateTime end = parseEndDateTimeFromArticle(element);
        if (start != null && end != null) {
            end = plausibleEnd(start, end, LocalDate.now(end.getZone()));
        }

        return GigSummary.GigSummaryBuilder.aGigSummary()
                .setCity(parseCityFromArticle(element))
                .setVenue(parseVenueFromArticle(element))
                .setUrl(url)
                .setBzId(parseBzId(url))
                .setBands(parseBandsFromArticle(element))
                .setTitle(parseTitleFromArticle(element))
                .setEntryFee(parseEntryFeeFromArticle(element))
                .setIsCancelled(parseIsCancelled(element))
                .setStart(start)
                .setEnd(end)
                .build();
    }

    /**
     * Bandzone renders an end given only as a time ("until 22:00") with TODAY's date — seen on
     * a 2020 concert whose end came out six years later. An end on today's date, more than two
     * weeks after a start that isn't today, is read as that time on the gig's own night.
     */
    static ZonedDateTime plausibleEnd(ZonedDateTime start, ZonedDateTime end, LocalDate today) {
        if (!end.toLocalDate().equals(today) || start.toLocalDate().equals(today)
                || !end.isAfter(start.plusDays(14))) {
            return end;
        }
        ZonedDateTime sameNight = start.toLocalDate().atTime(end.toLocalTime()).atZone(end.getZone());
        return sameNight.isBefore(start) ? sameNight.plusDays(1) : sameNight;
    }

    private String parseBzId(String url) {
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
                    .map(s -> s.charAt(s.length() - 1) == ',' ? s.substring(0, s.length() - 1) : s)
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
