package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.application.port.out.SettingsStore;

import java.util.Optional;

/** JPA-backed {@link SettingsStore}: table {@code app_setting}. */
@Component
@Transactional
public class JpaSettingsStore implements SettingsStore {

    private final AppSettingJpaRepository jpaRepository;

    public JpaSettingsStore(AppSettingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> get(String name) {
        return jpaRepository.findById(name).map(AppSettingEntity::getValue);
    }

    @Override
    public void put(String name, String value) {
        if (value == null) {
            jpaRepository.deleteById(name);
        } else {
            jpaRepository.save(new AppSettingEntity(name, value));
        }
    }
}
