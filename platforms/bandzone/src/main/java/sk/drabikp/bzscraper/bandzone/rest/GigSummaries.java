package sk.drabikp.bzscraper.bandzone.rest;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzoneProperties;
import sk.drabikp.bzscraper.bandzone.rest.api.GigSummaryJson;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.domain.EntryType;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The band's gigs from the catalog — the source of truth — with their Bandzone concert when
 * they are there. Only for the band configured for Bandzone ({@code bzscraper.bandzone.band-slug});
 * any other is "not found": the server never fetches pages for whoever asks.
 */
@Component
class GigSummaries {

    private final ListGigsUseCase listGigs;
    private final ListPublicationsUseCase listPublications;
    private final Platforms platforms;
    private final String bandSlug;

    GigSummaries(ListGigsUseCase listGigs, ListPublicationsUseCase listPublications, Platforms platforms,
                 BandzoneProperties bandzone) {
        this.listGigs = listGigs;
        this.listPublications = listPublications;
        this.platforms = platforms;
        this.bandSlug = bandzone.bandSlug();
    }

    /** The band's gigs, earliest first. */
    List<GigSummaryJson> ofBand(String requested) {
        if (bandSlug.isBlank() || !bandSlug.equalsIgnoreCase(requested)) {
            throw new NotFoundException("No gigs for band '" + requested + "' here.");
        }
        Map<GigId, Map<Platform, Publication>> published = listPublications.publicationsByGig();
        return listGigs.allGigs().stream()
                .sorted(Comparator.comparing(g -> g.schedule().start()))
                .map(gig -> summary(gig, published.getOrDefault(gig.id(), Map.of()).get(BandzonePlatform.PLATFORM)))
                .toList();
    }

    private GigSummaryJson summary(Gig gig, Publication publication) {
        String ref = publication == null || !publication.hasExternalRef() ? null : publication.externalRef();
        String url = ref == null ? null : platforms.traits(publication.platform()).eventUrl(ref);
        return new GigSummaryJson(ref, gig.title(), gig.schedule().start().toOffsetDateTime(),
                gig.schedule().end() == null ? null : gig.schedule().end().toOffsetDateTime(), url,
                gig.location().city(), gig.location().displayVenue(), gig.lineup(), entryFee(gig), gig.cancelled());
    }

    private static String entryFee(Gig gig) {
        return gig.admission().type() == EntryType.PAID ? gig.admission().amount()
                : gig.admission().type() == EntryType.VOLUNTARY ? "voluntary" : "free";
    }
}
