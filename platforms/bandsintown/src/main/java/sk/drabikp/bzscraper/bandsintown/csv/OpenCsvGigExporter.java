package sk.drabikp.bzscraper.bandsintown.csv;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandsintown.BandsintownProperties;
import sk.drabikp.bzscraper.catalog.application.port.out.GigExporter;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;

/**
 * Exports gigs as Bandsintown's 28-column bulk-import template (see
 * {@link BandsintownCsv}), for a manual import. Created gigs carry no Event Id — the
 * importer assigns one.
 */
@Component
class OpenCsvGigExporter implements GigExporter {

    private final String artistName;

    public OpenCsvGigExporter(BandsintownProperties properties) {
        this.artistName = properties.artistName();
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public String fileName() {
        return "bandsintown-gigs.csv";
    }

    @Override
    public String mediaType() {
        return "text/csv";
    }

    @Override
    public String export(List<Gig> gigs) {
        if (artistName.isBlank()) {
            throw new UserFacingException("Set bzscraper.bandsintown.artist-name (the artist as Bandsintown spells it) "
                    + "— every row of the file carries it.");
        }
        return BandsintownCsv.template(gigs, artistName);
    }
}
