package sk.drabikp.bzscraper.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.web.api.CsrfTokenJson;

import java.util.Optional;

/**
 * Signing in from the page with the one account: the CSRF token, who is signed in, and
 * starting a session (a fresh one — a session from before signing in is not reused) with the
 * "remember me" cookie when asked. Works on the current request (Spring's request proxies).
 */
@Component
class SignIn {

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final TokenBasedRememberMeServices rememberMe;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    SignIn(AuthenticationManager authentication, SecurityContextRepository contexts,
           TokenBasedRememberMeServices rememberMe, HttpServletRequest request, HttpServletResponse response) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.rememberMe = rememberMe;
        this.request = request;
        this.response = response;
    }

    /** The request's CSRF token; reading it also sets its cookie. */
    CsrfTokenJson csrfToken() {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new CsrfTokenJson(token.getHeaderName(), token.getToken());
    }

    Optional<String> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()
                ? Optional.empty() : Optional.of(auth.getName());
    }

    /** Signs in and returns the name; {@link WrongCredentialsException} when the name or password is wrong. */
    String signIn(String username, String password, boolean remember) {
        Authentication auth;
        try {
            auth = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    username == null ? "" : username.strip(), password == null ? "" : password));
        } catch (AuthenticationException e) {
            throw new WrongCredentialsException();
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        if (remember) {
            rememberMe.loginSuccess(request, response, auth);
        }
        return auth.getName();
    }
}
