package sk.drabikp.bzscraper.places.application;

import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.places.application.port.in.FindPlacesUseCase;
import sk.drabikp.bzscraper.places.application.port.out.PlaceSearch;
import sk.drabikp.bzscraper.places.domain.Town;
import sk.drabikp.bzscraper.places.domain.TownChoice;

import java.util.List;
import java.util.Optional;

/** Town suggestions from the {@link PlaceSearch}; resolving an address picks with {@link TownChoice}. */
public class PlaceService implements FindPlacesUseCase {

    private final PlaceSearch placeSearch;

    public PlaceService(PlaceSearch placeSearch) {
        this.placeSearch = placeSearch;
    }

    @Override
    public List<Town> search(String text) {
        return placeSearch.towns(text);
    }

    @Override
    public Optional<Town> resolve(String name, Country country, String postalCode) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return TownChoice.pick(name, country, null, postalCode, placeSearch.towns(name));
    }
}
