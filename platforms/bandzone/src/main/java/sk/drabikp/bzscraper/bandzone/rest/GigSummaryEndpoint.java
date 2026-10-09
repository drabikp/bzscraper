package sk.drabikp.bzscraper.bandzone.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.bandzone.BandzoneProperties;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
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
 * The band's gigs as JSON, from the catalog — the source of truth — in the shape this endpoint
 * has always had (it began as a Bandzone scraper): {@code bzId} and {@code url} are the gig's
 * concert on Bandzone, when it is there, and the band is the one configured for Bandzone
 * ({@code bzscraper.bandzone.band-slug}). Any other band is "not found" — the server never
 * fetches pages for whoever asks.
 */
@RestController
@RequestMapping("/gigs")
class GigSummaryEndpoint {

    private final ListGigsUseCase listGigs;
    private final ListPublicationsUseCase listPublications;
    private final Platforms platforms;
    private final String bandSlug;

    public GigSummaryEndpoint(ListGigsUseCase listGigs, ListPublicationsUseCase listPublications, Platforms platforms,
                              BandzoneProperties bandzone) {
        this.listGigs = listGigs;
        this.listPublications = listPublications;
        this.platforms = platforms;
        this.bandSlug = bandzone.bandSlug();
    }

    @GetMapping(path = "/{band_slug}")
    public List<GigSummaryResponse> getGigs(@PathVariable("band_slug") String requested) {
        if (bandSlug.isBlank() || !bandSlug.equalsIgnoreCase(requested)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No gigs for band '" + requested + "' here.");
        }
        Map<GigId, Map<Platform, Publication>> published = listPublications.publicationsByGig();
        return listGigs.allGigs().stream()
                .sorted(Comparator.comparing(g -> g.schedule().start()))
                .map(gig -> response(gig, published.getOrDefault(gig.id(), Map.of()).get(BandzonePlatform.PLATFORM)))
                .toList();
    }

    private GigSummaryResponse response(Gig gig, Publication publication) {
        String ref = publication == null || !publication.hasExternalRef() ? null : publication.externalRef();
        String url = ref == null ? null : platforms.traits(publication.platform()).eventUrl(ref);
        return new GigSummaryResponse(ref, gig.title(), gig.schedule().start(), gig.schedule().end(), url,
                gig.location().city(), gig.location().displayVenue(), gig.lineup(), entryFee(gig), gig.cancelled());
    }

    private static String entryFee(Gig gig) {
        return gig.admission().type() == EntryType.PAID ? gig.admission().amount()
                : gig.admission().type() == EntryType.VOLUNTARY ? "voluntary" : "free";
    }
}
