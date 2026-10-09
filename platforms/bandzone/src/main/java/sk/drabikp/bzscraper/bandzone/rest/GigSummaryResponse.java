package sk.drabikp.bzscraper.bandzone.rest;

import java.time.ZonedDateTime;
import java.util.List;

public record GigSummaryResponse(
        String bzId,
        String title,
        ZonedDateTime start,
        ZonedDateTime end,
        String url,
        String city,
        String venue,
        List<String> bands,
        String entryFee,
        boolean isCancelled
) {
}
