package sk.drabikp.bzscraper.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaSettingsStoreTest {

    @Autowired
    private JpaSettingsStore settings;

    @Test
    void keeps_replaces_and_removes_a_setting() {
        assertThat(settings.get("sync.paused")).isEmpty();

        settings.put("sync.paused", "true");
        settings.put("sync.paused", "false");
        assertThat(settings.get("sync.paused")).contains("false");

        settings.put("sync.paused", null);
        assertThat(settings.get("sync.paused")).isEmpty();
    }
}
