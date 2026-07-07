package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.util.List;

public interface GigProvider {
    List<GigSummary> findByBand(String bandSlug);
}
