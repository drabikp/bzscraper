package sk.drabikp.bzscraper.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Signing in from the page: {@code GET /csrf} hands out the CSRF token (and sets its cookie),
 * {@code POST /login} checks the account and starts the session (and the "remember me" cookie
 * when asked), {@code GET /me} says who is signed in. Signing out is {@code POST /logout}
 * ({@link SecurityConfiguration}).
 */
@RestController
@RequestMapping("/api/auth")
class AuthEndpoint {

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final TokenBasedRememberMeServices rememberMe;

    AuthEndpoint(AuthenticationManager authentication, SecurityContextRepository contexts,
                 TokenBasedRememberMeServices rememberMe) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.rememberMe = rememberMe;
    }

    record Login(String username, String password, boolean remember) {
    }

    record Me(String username) {
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/me")
    ResponseEntity<Me> me(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new Me(auth.getName()));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody Login login, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    login.username() == null ? "" : login.username().strip(),
                    login.password() == null ? "" : login.password()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "badCredentials", "message",
                    "Wrong name or password."));
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();                    // a session from before signing in is not reused
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        if (login.remember()) {
            rememberMe.loginSuccess(request, response, auth);
        }
        return ResponseEntity.ok(new Me(auth.getName()));
    }
}
