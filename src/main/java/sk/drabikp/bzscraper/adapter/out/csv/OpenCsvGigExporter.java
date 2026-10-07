package sk.drabikp.bzscraper.adapter.out.csv;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.adapter.out.bandsintown.BandsintownCsv;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.domain.model.Gig;

import java.util.List;

/**
 * Exports gigs as Bandsintown's 28-column bulk-import template (see
 * {@link BandsintownCsv}), for a manual import. Created gigs carry no Event Id — the
 * importer assigns one.
 */
@Component
public class OpenCsvGigExporter implements GigCsvExporter {

    private final String artistName;

    public OpenCsvGigExporter(
            @Value("${bzscraper.bandsintown.artist-name:Eufory (Band)}") String artistName) {
        this.artistName = artistName;
    }

    @Override
    public String export(List<Gig> gigs) {
        return BandsintownCsv.template(gigs, artistName);
    }
}
