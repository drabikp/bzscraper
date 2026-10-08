package sk.drabikp.bzscraper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.adapter.out.calendar.ProfileFile;
import sk.drabikp.bzscraper.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.model.RuleOrigin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Brings the stored band profile up to date on start. The shipped rules
 * ({@code calendar/base.profile} + the configured language presets) replace the
 * {@code PRESET} rules, so a new app version's presets take effect. The band's own file
 * ({@code bzscraper.calendar.profile-file}) replaces the {@code USER} rules — until the
 * rules editor exists it is where a band edits its own rules. A broken band file is
 * reported and the previous rules are kept. {@code LEARNED} rules are never touched here.
 */
@Component
class BandProfileSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BandProfileSync.class);

    private final BandProfileStore store;
    private final List<String> presets;
    private final String profileFile;

    BandProfileSync(BandProfileStore store,
                    @Value("${bzscraper.calendar.presets:sk-cz}") String presets,
                    @Value("${bzscraper.calendar.profile-file:./data/calendar-profile.txt}") String profileFile) {
        this.store = store;
        this.presets = Arrays.stream(presets.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        this.profileFile = profileFile == null ? "" : profileFile.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ProfileRule> shipped = new ArrayList<>(ProfileFile.parse(resource("base"), RuleOrigin.PRESET));
        presets.forEach(preset -> shipped.addAll(ProfileFile.parse(resource(preset), RuleOrigin.PRESET)));
        store.replace(RuleOrigin.PRESET, shipped);

        if (profileFile.isEmpty()) {
            return;
        }
        Path path = Path.of(profileFile);
        if (!Files.isRegularFile(path)) {
            log.info("No band profile file at {}; using the shipped rules only", path);
            return;
        }
        try {
            List<ProfileRule> own = ProfileFile.parse(Files.readString(path, StandardCharsets.UTF_8), RuleOrigin.USER);
            store.replace(RuleOrigin.USER, own);
            log.info("Band profile: {} shipped rules, {} from {}", shipped.size(), own.size(), path);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Band profile file {} not loaded ({}); keeping the previous band rules", path, e.getMessage());
        }
    }

    private static String resource(String name) {
        try {
            return new ClassPathResource("calendar/" + name + ".profile").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unknown calendar preset " + name, e);
        }
    }
}
