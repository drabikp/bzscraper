package sk.drabikp.bzscraper.adapter.out.places;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.PlaceSearch;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.Town;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PlaceSearch} on OpenStreetMap's Photon geocoder (photon.komoot.io — free, no key;
 * fair use: a person typing in a form is far below its limits). Towns and town parts only,
 * inside a box around Czechia and Slovakia, names as written locally. Photon's
 * {@code county} is the district as Bandzone writes it ("okres Přerov").
 */
@Component
public class PhotonPlaceSearch implements PlaceSearch {

    private static final Logger logger = LoggerFactory.getLogger(PhotonPlaceSearch.class);
    /** min lon, min lat, max lon, max lat — Czechia + Slovakia. */
    private static final String CZ_SK_BOX = "12.09,47.73,22.57,51.06";
    private static final int LIMIT = 8;

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public PhotonPlaceSearch(@Value("${bzscraper.places.photon-url:https://photon.komoot.io/api/}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public List<Town> towns(String text) {
        if (text == null || text.strip().length() < 2) {
            return List.of();
        }
        String url = baseUrl + "?q=" + URLEncoder.encode(text.strip(), StandardCharsets.UTF_8) + "&limit=" + LIMIT
                + "&bbox=" + CZ_SK_BOX + "&layer=city&layer=locality";
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "bzscraper-gig-sync/1.0 (a band's gig tool)")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Place search answered HTTP {}", response.statusCode());
                return List.of();
            }
            return parse(response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Place search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    List<Town> parse(String body) {
        Map<String, Object> root = json.readValue(body, Map.class);
        Map<String, Town> towns = new LinkedHashMap<>();         // one per name + district
        for (Object item : (List<Object>) root.getOrDefault("features", List.of())) {
            Map<String, Object> feature = (Map<String, Object>) item;
            Map<String, Object> p = (Map<String, Object>) feature.get("properties");
            List<Number> xy = (List<Number>) ((Map<String, Object>) feature.get("geometry")).get("coordinates");
            Country country = switch (String.valueOf(p.get("countrycode"))) {
                case "CZ" -> Country.CZECHIA;
                case "SK" -> Country.SLOVAKIA;
                default -> null;
            };
            String name = (String) p.get("name");
            if (country == null || name == null) {
                continue;
            }
            Town town = new Town(name, (String) p.get("county"), (String) p.get("state"), (String) p.get("postcode"),
                    country, xy.get(1).doubleValue(), xy.get(0).doubleValue());
            towns.putIfAbsent(name + "|" + town.district(), town);
        }
        return new ArrayList<>(towns.values());
    }
}
