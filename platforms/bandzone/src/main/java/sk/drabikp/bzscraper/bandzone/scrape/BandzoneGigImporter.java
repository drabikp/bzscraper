package sk.drabikp.bzscraper.bandzone.scrape;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzoneProperties;
import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Imports the configured band's Bandzone gigs (planned and played) by scraping its
 * public page. Each keeps its Bandzone concert id — the same id publishing records — so
 * importing links the catalog gig to the existing concert. Scraped gigs that can't form
 * a valid gig (no start/city) are dropped at the mapping boundary.
 */
@Component
class BandzoneGigImporter implements GigImporter {

    private final GigProvider gigProvider;
    private final String bandSlug;

    public BandzoneGigImporter(GigProvider gigProvider, BandzoneProperties properties) {
        this.gigProvider = gigProvider;
        this.bandSlug = properties.bandSlug();
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public List<ImportedGig> importGigs() throws BandzoneUploadException {
        if (bandSlug.isBlank()) {
            throw BandzoneUploadException.needsUser("Bandzone band slug not configured (bzscraper.bandzone.band-slug)");
        }
        List<ImportedGig> gigs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GigSummary summary : gigProvider.findByBand(bandSlug)) {
            if (summary.bzId() == null || summary.bzId().isBlank() || !seen.add(summary.bzId())) {
                continue;
            }
            GigSummaryToGigMapper.toGig(summary)
                    .ifPresent(gig -> gigs.add(new ImportedGig(BandzonePlatform.PLATFORM, gig, summary.bzId())));
        }
        return gigs;
    }
}
