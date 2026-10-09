package sk.drabikp.bzscraper.web;

/** Signing in with a wrong name or password: 401, code {@code badCredentials}. */
class WrongCredentialsException extends RuntimeException {

    WrongCredentialsException() {
        super("Wrong name or password.");
    }
}
