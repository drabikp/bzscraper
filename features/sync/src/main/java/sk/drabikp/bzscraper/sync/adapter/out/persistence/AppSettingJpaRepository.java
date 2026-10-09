package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AppSettingEntity}; used only by {@link JpaSettingsStore}. */
interface AppSettingJpaRepository extends JpaRepository<AppSettingEntity, String> {
}
