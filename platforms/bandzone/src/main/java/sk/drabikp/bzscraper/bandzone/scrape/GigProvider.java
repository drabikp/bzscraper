package sk.drabikp.bzscraper.bandzone.scrape;

import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;

import java.util.List;

interface GigProvider {
    List<GigSummary> findByBand(String bandSlug) throws BandzoneUploadException;
}
