package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA persistence model for one app setting. */
@Entity
@Table(name = "app_setting")
class AppSettingEntity {

    @Id
    private String name;
    @Column(name = "setting_value")
    private String value;

    protected AppSettingEntity() {
        // for JPA
    }

    AppSettingEntity(String name, String value) {
        this.name = name;
        this.value = value;
    }

    String getValue() {
        return value;
    }
}
