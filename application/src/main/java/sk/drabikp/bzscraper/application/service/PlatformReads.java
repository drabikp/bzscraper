package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.domain.model.Platform;

/** Reading a platform failed in a way its adapter didn't expect: logged in full, the user gets a short line. */
final class PlatformReads {

    private static final System.Logger log = System.getLogger(PlatformReads.class.getName());

    private PlatformReads() {
    }

    static String unexpected(Platform platform, RuntimeException e) {
        log.log(System.Logger.Level.ERROR, "Reading " + platform.id() + " failed unexpectedly", e);
        return "an unexpected error (" + e.getClass().getSimpleName() + ") — details are in the app's log";
    }
}
