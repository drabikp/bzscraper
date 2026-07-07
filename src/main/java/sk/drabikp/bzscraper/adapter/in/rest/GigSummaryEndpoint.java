package sk.drabikp.bzscraper.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import sk.drabikp.bzscraper.adapter.in.rest.dto.GigSummaryResponse;
import sk.drabikp.bzscraper.adapter.in.rest.dto.GigSummaryResponseMapper;
import sk.drabikp.bzscraper.application.port.in.GetGigsForBandUseCase;
import sk.drabikp.bzscraper.domain.model.BandNotFoundException;

import java.util.List;

@RestController
@RequestMapping("/gigs")
public class GigSummaryEndpoint {

    private final GetGigsForBandUseCase getGigsForBand;
    private final GigSummaryResponseMapper mapper;

    public GigSummaryEndpoint(GetGigsForBandUseCase getGigsForBand, GigSummaryResponseMapper mapper) {
        this.getGigsForBand = getGigsForBand;
        this.mapper = mapper;
    }

    @GetMapping(path = "/{band_slug}")
    public List<GigSummaryResponse> getGigs(@PathVariable("band_slug") String bandSlug) {
        try {
            return mapper.toResponses(getGigsForBand.byBand(bandSlug));
        } catch (BandNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }
}
