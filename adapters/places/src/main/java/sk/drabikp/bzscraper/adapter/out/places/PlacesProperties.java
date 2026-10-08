package sk.drabikp.bzscraper.adapter.out.places;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bzscraper.places.*}: the Photon place search to ask ({@code photon-url}). */
@ConfigurationProperties("bzscraper.places")
public record PlacesProperties(String photonUrl) {

    public PlacesProperties {
        photonUrl = photonUrl == null || photonUrl.isBlank() ? "https://photon.komoot.io/api/" : photonUrl;
    }
}
