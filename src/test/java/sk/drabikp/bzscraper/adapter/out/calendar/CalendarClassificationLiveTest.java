package sk.drabikp.bzscraper.adapter.out.calendar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import sk.drabikp.bzscraper.domain.model.BandProfile;
import sk.drabikp.bzscraper.domain.model.CalendarClassification;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.model.RuleOrigin;
import sk.drabikp.bzscraper.domain.service.CalendarEventClassifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the REAL band calendar and prints how the shipped presets plus the band's own
 * rules sort it — for checking rule changes by eye. Read-only. Skipped unless
 * {@code CAL_LIVE=true}; reads {@code CAL_URL} (the private iCal address — never commit
 * it), optional {@code CAL_PROFILE} (the band's rules file) and {@code CAL_CATALOG_DAYS}
 * (a file of ISO dates the catalog has gigs on, one per line).
 */
@EnabledIfEnvironmentVariable(named = "CAL_LIVE", matches = "true")
class CalendarClassificationLiveTest {

    @Test
    void sorts_the_real_calendar() throws Exception {
        List<CalendarEvent> events = new IcsCalendarFeed(System.getenv("CAL_URL"), "Europe/Prague").read();

        List<ProfileRule> rules = new ArrayList<>();
        for (String preset : List.of("base", "sk-cz")) {
            rules.addAll(ProfileFile.parse(new ClassPathResource("calendar/" + preset + ".profile")
                    .getContentAsString(StandardCharsets.UTF_8), RuleOrigin.PRESET));
        }
        String own = System.getenv("CAL_PROFILE");
        if (own != null && !own.isBlank()) {
            rules.addAll(ProfileFile.parse(Files.readString(Path.of(own)), RuleOrigin.USER));
        }
        String daysFile = System.getenv("CAL_CATALOG_DAYS");
        Set<LocalDate> catalogDays = daysFile == null || daysFile.isBlank() ? Set.of()
                : Files.readAllLines(Path.of(daysFile)).stream().map(String::strip).filter(s -> !s.isEmpty())
                .map(LocalDate::parse).collect(Collectors.toSet());

        List<CalendarClassification> results = CalendarEventClassifier.classify(events,
                new BandProfile(rules, new BandProfile.Thresholds(4, -1, -4)), catalogDays, Map.of());

        Map<CalendarEventKind, Long> counts = results.stream()
                .collect(Collectors.groupingBy(CalendarClassification::kind, Collectors.counting()));
        System.out.println(events.size() + " events: " + counts);
        for (CalendarEventKind kind : List.of(CalendarEventKind.GIG, CalendarEventKind.UNSURE)) {
            System.out.println("== " + kind);
            results.stream().filter(c -> c.kind() == kind)
                    .sorted(Comparator.comparing(c -> c.event().start()))
                    .forEach(CalendarClassificationLiveTest::print);
        }
        System.out.println("== NOT_GIG close to the line (score > -4)");
        results.stream().filter(c -> c.kind() == CalendarEventKind.NOT_GIG && c.score() > -4)
                .sorted(Comparator.comparing(c -> c.event().start()))
                .forEach(CalendarClassificationLiveTest::print);

        assertThat(events).isNotEmpty();
    }

    private static void print(CalendarClassification c) {
        System.out.printf("  %s  %-48.48s %3d %-9s %s%n", c.event().day(), c.event().title(), c.score(),
                c.status(), c.reasons().stream().map(r -> r.text() + " " + r.weight()).collect(Collectors.joining("; ")));
    }
}
