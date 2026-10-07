package sk.drabikp.bzscraper.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * On every start, writes the whole H2 database as a plain SQL script ({@code SCRIPT TO})
 * to {@code bzscraper.db.backup.dir}, one file per day, keeping the newest
 * {@code bzscraper.db.backup.keep}. A SQL script loads into any later H2 version (whose
 * binary file format may not) and is readable by hand. A failed backup only logs a
 * warning; it never stops the app.
 */
@Component
@ConditionalOnProperty(name = "bzscraper.db.backup.enabled", havingValue = "true", matchIfMissing = true)
public class H2ScriptBackup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(H2ScriptBackup.class);
    private static final String PREFIX = "bzscraper-";

    private final DataSource dataSource;
    private final Path dir;
    private final int keep;

    public H2ScriptBackup(DataSource dataSource,
                          @Value("${bzscraper.db.backup.dir:./data/backups}") String dir,
                          @Value("${bzscraper.db.backup.keep:14}") int keep) {
        this.dataSource = dataSource;
        this.dir = Paths.get(dir);
        this.keep = Math.max(1, keep);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path file = backup(LocalDate.now());
            prune();
            log.info("Database backed up to {}", file);
        } catch (IOException | SQLException e) {
            log.warn("Database backup to {} failed: {}", dir, e.getMessage());
        }
    }

    Path backup(LocalDate day) throws IOException, SQLException {
        Files.createDirectories(dir);
        Path file = dir.resolve(PREFIX + day + ".sql").toAbsolutePath();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SCRIPT TO '" + file.toString().replace("'", "''") + "'");
        }
        return file;
    }

    void prune() throws IOException {
        List<Path> backups;
        try (Stream<Path> files = Files.list(dir)) {
            backups = files
                    .filter(f -> f.getFileName().toString().startsWith(PREFIX))
                    .filter(f -> f.getFileName().toString().endsWith(".sql"))
                    .sorted() // ISO dates in the name sort chronologically
                    .toList();
        }
        for (Path old : backups.subList(0, Math.max(0, backups.size() - keep))) {
            Files.delete(old);
        }
    }
}
