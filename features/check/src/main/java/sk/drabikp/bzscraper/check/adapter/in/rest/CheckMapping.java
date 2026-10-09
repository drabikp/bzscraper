package sk.drabikp.bzscraper.check.adapter.in.rest;

import sk.drabikp.bzscraper.check.adapter.in.rest.api.CheckResultJson;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.CheckStateJson;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.DriftDifferenceJson;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.DriftFieldJson;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.DriftJson;
import sk.drabikp.bzscraper.check.adapter.in.rest.api.DriftKindJson;
import sk.drabikp.bzscraper.check.domain.Drift;
import sk.drabikp.bzscraper.check.domain.PlatformCheck;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The platform check as the API sends it (check.yaml). */
final class CheckMapping {

    private CheckMapping() {
    }

    static CheckStateJson toJson(boolean running, Optional<PlatformCheck> last) {
        return new CheckStateJson(running, last.map(CheckMapping::toJson).orElse(null));
    }

    private static CheckResultJson toJson(PlatformCheck result) {
        Map<String, String> unreadable = new TreeMap<>();
        result.unreadable().forEach((p, why) -> unreadable.put(p.id(), why));
        Map<String, Integer> unlinked = new TreeMap<>();
        result.unlinked().forEach((p, n) -> unlinked.put(p.id(), n));
        return new CheckResultJson(result.checkedAt(), result.drifts().stream().map(CheckMapping::toJson).toList(),
                unreadable, unlinked);
    }

    private static DriftJson toJson(Drift d) {
        return new DriftJson(d.platform().id(), d.gigId().token(), d.gigLabel(), d.externalRef(),
                DriftKindJson.fromValue(d.kind().name()), d.differences().stream()
                .map(x -> new DriftDifferenceJson(DriftFieldJson.fromValue(x.field().name()), x.catalog(),
                        x.platform(), x.text()))
                .toList());
    }
}
