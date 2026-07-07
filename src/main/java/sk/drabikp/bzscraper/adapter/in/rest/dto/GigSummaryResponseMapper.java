package sk.drabikp.bzscraper.adapter.in.rest.dto;

import org.mapstruct.Mapper;
import sk.drabikp.bzscraper.domain.model.GigSummary;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GigSummaryResponseMapper {
    GigSummaryResponse toResponse(GigSummary gig);

    List<GigSummaryResponse> toResponses(List<GigSummary> gigs);
}
