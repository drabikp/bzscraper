package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.notification.Notification;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;
import sk.drabikp.bzscraper.domain.model.PublishStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders a {@link PublishResult} list as a per-platform notification. Shared by the views. */
final class PublishSummaries {

    private PublishSummaries() {
    }

    static void show(List<PublishResult> results) {
        Map<Platform, List<PublishResult>> byPlatform =
                results.stream().collect(Collectors.groupingBy(PublishResult::platform));

        String msg = Arrays.stream(Platform.values())
                .filter(byPlatform::containsKey)
                .map(p -> platformLine(p, byPlatform.get(p)))
                .collect(Collectors.joining("   ·   "));

        boolean anyFailed = results.stream().anyMatch(r -> r.status() == PublishStatus.FAILED);
        Notification.show(msg, 6000,
                anyFailed ? Notification.Position.MIDDLE : Notification.Position.BOTTOM_START);
    }

    private static String platformLine(Platform platform, List<PublishResult> results) {
        long published = countOf(results, PublishStatus.PUBLISHED);
        long already = countOf(results, PublishStatus.SKIPPED_ALREADY_UPLOADED);
        long invalid = countOf(results, PublishStatus.SKIPPED_INVALID);
        long failed = countOf(results, PublishStatus.FAILED);

        StringBuilder sb = new StringBuilder(label(platform)).append(": ")
                .append(published).append(" published");
        if (already > 0) {
            sb.append(", ").append(already).append(" already up");
        }
        if (invalid > 0) {
            sb.append(", ").append(invalid).append(" skipped");
        }
        if (failed > 0) {
            sb.append(", ").append(failed).append(" failed");
            results.stream()
                    .filter(r -> r.status() == PublishStatus.FAILED)
                    .map(PublishResult::detail)
                    .filter(d -> d != null && !d.isBlank())
                    .findFirst()
                    .ifPresent(detail -> sb.append(" (").append(detail).append(")"));
        }
        return sb.toString();
    }

    private static long countOf(List<PublishResult> results, PublishStatus status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    static String label(Platform platform) {
        return switch (platform) {
            case BANDZONE -> "Bandzone";
            case BANDSINTOWN -> "Bandsintown";
        };
    }
}
