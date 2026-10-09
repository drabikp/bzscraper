package sk.drabikp.bzscraper.gig.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bzscraper.db.backup.*}: whether the daily SQL backup is written ({@code enabled}, on
 * unless {@code false} — read by {@link H2ScriptBackup}'s condition), where it goes and how many
 * are kept.
 */
@ConfigurationProperties("bzscraper.db.backup")
public record BackupProperties(Boolean enabled, String dir, Integer keep) {

    public BackupProperties {
        enabled = enabled == null || enabled;
        dir = dir == null || dir.isBlank() ? "./data/backups" : dir;
        keep = keep == null ? 14 : Math.max(1, keep);
    }
}
