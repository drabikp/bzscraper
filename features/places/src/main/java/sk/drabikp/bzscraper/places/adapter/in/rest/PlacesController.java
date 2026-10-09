package sk.drabikp.bzscraper.places.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.gig.api.CountryJson;
import sk.drabikp.bzscraper.gig.api.GigDraftMapping;
import sk.drabikp.bzscraper.places.adapter.in.rest.api.PlacesApi;
import sk.drabikp.bzscraper.places.adapter.in.rest.api.TownJson;
import sk.drabikp.bzscraper.places.application.port.in.FindPlacesUseCase;

import java.util.List;

/** Serves the town picker (places.yaml). */
@RestController
class PlacesController implements PlacesApi {

    private final FindPlacesUseCase places;

    PlacesController(FindPlacesUseCase places) {
        this.places = places;
    }

    @Override
    public ResponseEntity<List<TownJson>> searchTowns(String q) {
        return ResponseEntity.ok(TownMapping.toJson(places.search(q)));
    }

    @Override
    public ResponseEntity<TownJson> resolveTown(String name, CountryJson country, String postalCode) {
        return places.resolve(name, GigDraftMapping.toCountry(country), postalCode)
                .map(TownMapping::toJson).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
