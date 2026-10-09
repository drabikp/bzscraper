package sk.drabikp.bzscraper.calendar.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sk.drabikp.bzscraper.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.gig.api.ProblemJson;

import java.util.Map;

/** The calendar couldn't be read (unreachable, not a calendar): 502, code {@code calendarUnavailable}. */
@RestControllerAdvice(assignableTypes = CalendarController.class)
class CalendarErrors {

    @ExceptionHandler(CalendarUnavailableException.class)
    ResponseEntity<ProblemJson> unavailable(CalendarUnavailableException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ProblemJson("calendarUnavailable", Map.of(), e.getMessage()));
    }
}
