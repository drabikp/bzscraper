package sk.drabikp.bzscraper.check.application.port.in;

/** Starts a platform check in the background (it reads the platforms — slow); nothing when one runs. */
public interface StartPlatformCheckUseCase {

    void start();
}
