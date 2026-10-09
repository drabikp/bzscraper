package sk.drabikp.bzscraper.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import sk.drabikp.bzscraper.gig.api.ProblemJson;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.application.UserFacingException;

import java.util.Map;

/**
 * How the API says no: a {@link ProblemJson} ({@code {code, args, message}}, gig.yaml). A rule that
 * refused ({@link UserFacingException}) is a 409 with its code and arguments (the page says it in
 * the user's language, the message is the English fallback); something not there is a 404; a
 * wrong password a 401; a request that makes no sense is a 400; anything else is a fault —
 * logged in full, the page gets a short line.
 */
@RestControllerAdvice
class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(UserFacingException.class)
    ResponseEntity<ProblemJson> refused(UserFacingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ProblemJson(e.code() != null ? e.code() : "refused", e.args(), e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemJson> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ProblemJson("notFound", Map.of(), e.getMessage()));
    }

    @ExceptionHandler(WrongCredentialsException.class)
    ResponseEntity<ProblemJson> wrongCredentials(WrongCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ProblemJson("badCredentials", Map.of(), e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemJson> badRequest(Exception e) {
        log.debug("Bad request", e);
        return ResponseEntity.badRequest().body(new ProblemJson("badRequest", Map.of(), e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemJson> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ProblemJson(
                e.getStatusCode().value() == 404 ? "notFound" : "error", Map.of(), e.getReason()));
    }

    /** The page went away mid-answer (a phone closing the live updates): nobody to tell. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void gone(AsyncRequestNotUsableException e) {
        log.debug("The page went away", e);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemJson> fault(Exception e) throws Exception {
        if (e instanceof org.springframework.security.access.AccessDeniedException
                || e instanceof org.springframework.security.core.AuthenticationException) {
            throw e;                                      // Spring Security answers these itself
        }
        if (e instanceof ErrorResponse response) {          // Spring's own: not found, wrong method, …
            int status = response.getStatusCode().value();
            return ResponseEntity.status(status).body(new ProblemJson(status == 404 ? "notFound" : "badRequest", Map.of(),
                    response.getBody().getDetail()));
        }
        log.error("An API call failed", e);
        return ResponseEntity.internalServerError().body(new ProblemJson("unexpected", Map.of(),
                "Something went wrong — nothing may have changed. Details are in the log."));
    }
}
