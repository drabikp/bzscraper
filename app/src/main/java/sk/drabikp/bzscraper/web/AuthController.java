package sk.drabikp.bzscraper.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.web.api.AuthApi;
import sk.drabikp.bzscraper.web.api.CsrfTokenJson;
import sk.drabikp.bzscraper.web.api.LoginJson;
import sk.drabikp.bzscraper.web.api.MeJson;

/** Serves signing in from the page (auth.yaml); signing out is the security filter's ({@link SecurityConfiguration}). */
@RestController
class AuthController implements AuthApi {

    private final SignIn signIn;

    AuthController(SignIn signIn) {
        this.signIn = signIn;
    }

    @Override
    public ResponseEntity<CsrfTokenJson> csrfToken() {
        return ResponseEntity.ok(signIn.csrfToken());
    }

    @Override
    public ResponseEntity<MeJson> currentUser() {
        return signIn.currentUser().map(name -> ResponseEntity.ok(new MeJson(name)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @Override
    public ResponseEntity<MeJson> login(LoginJson login) {
        return ResponseEntity.ok(new MeJson(signIn.signIn(login.getUsername(), login.getPassword(),
                Boolean.TRUE.equals(login.getRemember()))));
    }
}
