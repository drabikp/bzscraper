package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformCapabilities;
import sk.drabikp.bzscraper.domain.model.PlatformSupport;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * What each platform can do with past events, as its adapters declare it — not
 * configuration: a platform without an adapter for an action can't do it at all. Edits
 * run as a workflow, where each step says itself what it takes (see {@link WorkflowEngine}).
 */
public final class AdapterCapabilities {

    private AdapterCapabilities() {
    }

    public static PlatformSupport of(Collection<GigPublisher> publishers, Collection<GigWithdrawer> withdrawers) {
        Map<Platform, PlatformCapabilities> capabilities = new EnumMap<>(Platform.class);
        for (Platform platform : Platform.values()) {
            Optional<GigPublisher> publisher = publishers.stream().filter(p -> p.platform() == platform).findFirst();
            Optional<GigWithdrawer> withdrawer = withdrawers.stream().filter(w -> w.platform() == platform).findFirst();
            capabilities.put(platform, new PlatformCapabilities(
                    publisher.map(GigPublisher::publishesPastEvents).orElse(false),
                    withdrawer.map(w -> w.withdrawsPastEvents(WithdrawAction.CANCEL)).orElse(false),
                    withdrawer.map(w -> w.withdrawsPastEvents(WithdrawAction.DELETE)).orElse(false)));
        }
        return new PlatformSupport(capabilities);
    }
}
