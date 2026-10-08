package sk.drabikp.bzscraper.adapter.out.persistence;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.Clock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class H2ScriptBackupTest {

    @TempDir
    Path dir;

    private static JdbcDataSource database() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:backup-test;DB_CLOSE_DELAY=-1");
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().execute("create table if not exists gig (id varchar(10) primary key)");
            c.createStatement().execute("merge into gig key (id) values ('x')");
        }
        return dataSource;
    }

    @Test
    void writes_the_database_as_a_sql_script_named_by_day() throws Exception {
        H2ScriptBackup backup = new H2ScriptBackup(database(), new BackupProperties(true, dir.toString(), 14),
                Clock.systemDefaultZone());

        Path file = backup.backup(LocalDate.of(2026, 10, 7));

        assertThat(file.getFileName().toString()).isEqualTo("bzscraper-2026-10-07.sql");
        assertThat(Files.readString(file)).containsIgnoringCase("CREATE").contains("'x'");
    }

    @Test
    void keeps_only_the_newest_backups() throws Exception {
        H2ScriptBackup backup = new H2ScriptBackup(database(), new BackupProperties(true, dir.toString(), 2),
                Clock.systemDefaultZone());
        backup.backup(LocalDate.of(2026, 10, 5));
        backup.backup(LocalDate.of(2026, 10, 6));
        backup.backup(LocalDate.of(2026, 10, 7));
        Files.writeString(dir.resolve("unrelated.txt"), "keep me");

        backup.prune();

        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .containsExactlyInAnyOrder("bzscraper-2026-10-06.sql", "bzscraper-2026-10-07.sql", "unrelated.txt");
        }
    }
}
