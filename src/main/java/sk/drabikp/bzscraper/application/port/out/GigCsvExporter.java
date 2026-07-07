package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Gig;

import java.util.List;

public interface GigCsvExporter {
    String export(List<Gig> gigs);
}
