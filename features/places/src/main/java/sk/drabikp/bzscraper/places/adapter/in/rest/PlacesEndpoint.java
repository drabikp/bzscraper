package sk.drabikp.bzscraper.places.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.places.application.port.in.FindPlacesUseCase;
import sk.drabikp.bzscraper.places.domain.Town;

import java.util.List;

/** The town picker: towns by what was typed, and the one town that fits a name, country and postal code. */
@RestController
@RequestMapping("/api/places")
class PlacesEndpoint {

    private final FindPlacesUseCase places;

    PlacesEndpoint(FindPlacesUseCase places) {
        this.places = places;
    }

    record TownJson(String name, String district, String region, String postalCode, Country country,
                    Double latitude, Double longitude) {

        static TownJson of(Town town) {
            return new TownJson(town.name(), town.district(), town.region(), town.postalCode(), town.country(),
                    town.latitude(), town.longitude());
        }
    }

    @GetMapping
    List<TownJson> search(@RequestParam(defaultValue = "") String q) {
        return places.search(q).stream().filter(Town::resolved).map(TownJson::of).toList();
    }

    /** The town when exactly one fits; 204 No Content otherwise. */
    @GetMapping("/resolve")
    ResponseEntity<TownJson> resolve(@RequestParam String name, @RequestParam(required = false) Country country,
                                     @RequestParam(required = false) String postalCode) {
        return places.resolve(name, country, postalCode).filter(Town::resolved).map(TownJson::of)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
