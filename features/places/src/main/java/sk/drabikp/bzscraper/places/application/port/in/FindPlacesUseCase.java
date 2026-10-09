package sk.drabikp.bzscraper.places.application.port.in;

import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.places.domain.Town;

import java.util.List;
import java.util.Optional;

/** Towns for the gig form: suggestions while typing, and the town a known address means. */
public interface FindPlacesUseCase {

    List<Town> search(String text);

    /** The one town that fits (name, country, postal area), or empty when none or several do. */
    Optional<Town> resolve(String name, Country country, String postalCode);
}
