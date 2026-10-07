package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;

import java.util.Map;

/** Where each catalog gig is published: per gig, its copy on each platform. */
public interface ListPublicationsUseCase {

    /** Gigs never published anywhere are absent. */
    Map<GigId, Map<Platform, Publication>> publicationsByGig();
}
