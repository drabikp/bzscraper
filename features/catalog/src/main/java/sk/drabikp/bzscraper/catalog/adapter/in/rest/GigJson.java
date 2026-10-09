package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigDraft;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A gig as the pages get it: its form fields ({@link GigDraft}), and per platform whether it is
 * there and what the sync is doing with it.
 *
 * @param id  the gig's identity as a token for its address
 * @param rev the gig's revision: an edit names the revision it was made on, so a gig changed
 *            meanwhile is not overwritten
 */
record GigJson(String id, String rev, boolean past, GigDraft gig, List<PlatformState> platforms) {

    /**
     * One platform: {@code state} is {@code live} (published, nothing to do), {@code none}
     * (not there), {@code queued}, {@code running}, {@code retrying} (failed, tried again at
     * {@code nextAttemptAt}) or {@code failed} (waits for the user).
     */
    record PlatformState(String platform, String state, String ref, String url, Long taskId, String action,
                         int attempts, String nextAttemptAt, String message) {
    }

    static GigJson of(Gig gig, Map<Platform, Publication> published, List<SyncTask> openTasks, Platforms platforms,
                      Clock clock) {
        List<PlatformState> states = platforms.all().stream().map(platform -> {
            Publication publication = published.get(platform);
            String ref = publication != null && publication.hasExternalRef() ? publication.externalRef() : null;
            String url = ref != null ? platforms.traits(platform).eventUrl(ref) : null;
            Optional<SyncTask> task = openTasks.stream()
                    .filter(t -> t.platform().equals(platform) && t.gigId().equals(gig.id()))
                    .max(Comparator.comparingLong(SyncTask::id));
            if (task.isEmpty()) {
                return new PlatformState(platform.id(), publication != null ? "live" : "none", ref, url, null, null,
                        0, null, null);
            }
            SyncTask t = task.get();
            String state = t.status() == SyncStatus.FAILED ? "failed" : t.status() == SyncStatus.RUNNING ? "running"
                    : t.retrying() ? "retrying" : "queued";
            return new PlatformState(platform.id(), state, ref, url, t.id(), t.action().name(), t.attempts(),
                    t.nextAttemptAt() != null ? t.nextAttemptAt().toString() : null, t.message());
        }).toList();
        return new GigJson(gig.id().token(), rev(gig), gig.isPast(clock), GigDraft.of(gig), states);
    }

    /** The gig's revision: a digest of everything it says. */
    static String rev(Gig gig) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(gig.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
