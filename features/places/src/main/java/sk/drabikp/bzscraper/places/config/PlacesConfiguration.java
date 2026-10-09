package sk.drabikp.bzscraper.places.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.places.application.PlaceService;
import sk.drabikp.bzscraper.places.application.port.out.PlaceSearch;

@Configuration
class PlacesConfiguration {

    @Bean
    PlaceService placeService(PlaceSearch placeSearch) {
        return new PlaceService(placeSearch);
    }
}
