package sk.drabikp.bzscraper.domain.model;

import java.util.EnumMap;
import java.util.Map;

/** The {@link PlatformCapabilities} of every platform ({@link #ALL}: every platform can do everything — tests). */
public final class PlatformSupport {

    public static final PlatformSupport ALL = new PlatformSupport(Map.of());

    private final Map<Platform, PlatformCapabilities> capabilities;

    public PlatformSupport(Map<Platform, PlatformCapabilities> capabilities) {
        this.capabilities = capabilities.isEmpty() ? Map.of() : new EnumMap<>(capabilities);
    }

    public PlatformCapabilities of(Platform platform) {
        return capabilities.getOrDefault(platform, PlatformCapabilities.ALL);
    }
}
