package com.waydee.traffic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Bir giriş denemesi: kim, nereden, hangi cihazla, başarılı mı. */
@Entity
@Table(name = "login_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", nullable = false, length = 60)
    private String username;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "country", length = 60)
    private String country;

    @Column(name = "device", length = 20)
    private String device;

    @Column(name = "browser", length = 40)
    private String browser;

    @Column(name = "os", length = 40)
    private String os;

    @Column(name = "surface", nullable = false, length = 10)
    private String surface;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public LoginEvent(UUID userId, String username, String ip, String country, String device,
                      String browser, String os, String surface, boolean success, String userAgent) {
        this.userId = userId;
        this.username = username;
        this.ip = ip;
        this.country = country;
        this.device = device;
        this.browser = browser;
        this.os = os;
        this.surface = surface;
        this.success = success;
        this.userAgent = userAgent != null && userAgent.length() > 400 ? userAgent.substring(0, 400) : userAgent;
        this.createdAt = Instant.now();
    }
}
