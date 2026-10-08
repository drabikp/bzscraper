package sk.drabikp.bzscraper.adapter.in.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import sk.drabikp.bzscraper.adapter.in.rest.dto.GigSummaryResponse;
import sk.drabikp.bzscraper.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.domain.model.EntryType;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Platforms;
import sk.drabikp.bzscraper.domain.model.Publication;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The band's gigs as JSON, from the catalog — the source of truth — in the shape this endpoint
 * has always had: {@code bzId} and {@code url} are the gig's concert on Bandzone (the platform
 * the endpoint was built on), when it is there. Only the configured band is served; anything
 * else is "not found" — the server never fetches pages for whoever asks.
 */
@RestController
@RequestMapping("/gigs")
public class GigSummaryEndpoint {

    /** The platform whose event id and page the response's {@code bzId}/{@code url} carry. */
    private static final String LINKED_PLATFORM = "BANDZONE";

    private final ListGigsUseCase listGigs;
    private final ListPublicationsUseCase listPublications;
    private final Platforms platforms;
    private final String bandSlug;

    public GigSummaryEndpoint(ListGigsUseCase listGigs, ListPublicationsUseCase listPublications, Platforms platforms,
                              @Value("${bzscraper.bandzone.band-slug:}") String bandSlug) {
        this.listGigs = listGigs;
        this.listPublications = listPublications;
        this.platforms = platforms;
        this.bandSlug = bandSlug;
    }

    @GetMapping(path = "/{band_slug}")
    public List<GigSummaryResponse> getGigs(@PathVariable("band_slug") String requested) {
        if (bandSlug.isBlank() || !bandSlug.equalsIgnoreCase(requested)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No gigs for band '" + requested + "' here.");
        }
        Optional<Platform> linked = platforms.find(LINKED_PLATFORM);
        Map<GigId, Map<Platform, Publication>> published = listPublications.publicationsByGig();
        return listGigs.allGigs().stream()
                .sorted(Comparator.comparing(g -> g.schedule().start()))
                .map(gig -> response(gig, linked.map(p -> published.getOrDefault(gig.id(), Map.of()).get(p))
                        .orElse(null)))
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
