package sk.drabikp.bzscraper.adapter.out.places;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Town;

import static org.assertj.core.api.Assertions.assertThat;

/** Photon's answer, shaped as seen on 2026-10-08 (q=Košice, local names, CZ/SK box). */
class PhotonPlaceSearchTest {

    private static final String KOSICE = """
            {"type":"FeatureCollection","features":[
             {"geometry":{"coordinates":[21.25,48.717],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"SK","name":"Košice","county":"okres Košice I","state":"Košický kraj","postcode":"041 26",
              "type":"city","osm_value":"city"}},
             {"geometry":{"coordinates":[15.151,49.896],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"CZ","name":"Košice","county":"okres Kutná Hora","state":"Středočeský kraj",
              "type":"city","osm_value":"municipality"}},
             {"geometry":{"coordinates":[15.151,49.897],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"CZ","name":"Košice","county":"okres Kutná Hora","state":"Středočeský kraj",
              "type":"locality","osm_value":"locality"}},
             {"geometry":{"coordinates":[16.0,47.0],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"AT","name":"Somewhere","type":"city"}}]}
            """;

    @Test
    void towns_come_with_district_region_postal_code_and_coordinates_once_each() {
        assertThat(new PhotonPlaceSearch("http://unused").parse(KOSICE)).containsExactly(
                new Town("Košice", "okres Košice I", "Košický kraj", "041 26", Country.SLOVAKIA, 48.717, 21.25),
                new Town("Košice", "okres Kutná Hora", "Středočeský kraj", null, Country.CZECHIA, 49.896, 15.151));
    }

    @Test
    void an_unreachable_search_gives_no_suggestions() {
        assertThat(new PhotonPlaceSearch("http://127.0.0.1:9/").towns("Košice")).isEmpty();
    }
}
