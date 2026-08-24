package com.waydee.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 60)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public void update(String newValue) {
        this.value = newValue;
        this.updatedAt = Instant.now();
    }
}
