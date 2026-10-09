package sk.drabikp.bzscraper.places.adapter.in.rest;

import sk.drabikp.bzscraper.gig.api.GigDraftMapping;
import sk.drabikp.bzscraper.places.adapter.in.rest.api.TownJson;
import sk.drabikp.bzscraper.places.domain.Town;

import java.util.List;

/** Towns as the API sends them (places.yaml). */
final class TownMapping {

    private TownMapping() {
    }

    static TownJson toJson(Town town) {
        return new TownJson(town.name(), town.district(), town.region(), town.postalCode(),
                GigDraftMapping.toJson(town.country()), town.latitude(), town.longitude());
    }

    static List<TownJson> toJson(List<Town> towns) {
        return towns.stream().map(TownMapping::toJson).toList();
    }
}
