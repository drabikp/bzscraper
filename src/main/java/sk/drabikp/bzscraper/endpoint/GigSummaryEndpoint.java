package sk.drabikp.bzscraper.endpoint;

import org.jsoup.HttpStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import sk.drabikp.bzscraper.gigsummary.GigSummary;
import sk.drabikp.bzscraper.gigsummary.GigSummaryDto;
import sk.drabikp.bzscraper.service.GigSummaryService;

import java.util.List;

@RestController
@RequestMapping("/gigs")
public class GigSummaryEndpoint {

    private final GigSummaryService gigSummaryService;

    @Autowired
    public GigSummaryEndpoint(GigSummaryService gigSummaryService) {
        this.gigSummaryService = gigSummaryService;
    }

    @GetMapping(path = "/{band_slug}")
    public GigSummaryDto getGigs(@PathVariable("band_slug") String bandSlug) {
        try {
            List<GigSummary> allGigsForBand = gigSummaryService.getAllGigsForBand(bandSlug);
            return new GigSummaryDto(allGigsForBand);
        } catch (Exception e) {
            if (e.getCause() instanceof HttpStatusException ex) {
                if (ex.getStatusCode() == 404) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, bandSlug +" not found", ex);
                }
            }
            throw e;
        }
    }
}
