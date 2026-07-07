package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.util.List;

public interface GetGigsForBandUseCase {
    List<GigSummary> byBand(String bandSlug);
}
