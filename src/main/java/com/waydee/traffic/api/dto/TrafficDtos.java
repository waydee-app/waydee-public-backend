package com.waydee.traffic.api.dto;

import com.waydee.traffic.domain.LoginEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TrafficDtos {

    private TrafficDtos() {
    }

    public record CountRow(String label, long total, long users) {
    }

    public record DayCount(String date, long count) {
    }

    public record TopUserRow(String username, long logins, Instant lastLogin, long countries) {
    }

    public record LoginRow(
            UUID id,
            UUID userId,
            String username,
            String ip,
            String country,
            String device,
            String browser,
            String os,
            String surface,
            boolean success,
            Instant createdAt
    ) {
        public static LoginRow from(LoginEvent e) {
            return new LoginRow(e.getId(), e.getUserId(), e.getUsername(), e.getIp(), e.getCountry(),
                    e.getDevice(), e.getBrowser(), e.getOs(), e.getSurface(), e.isSuccess(), e.getCreatedAt());
        }
    }

    /**
     * Kullanıcının KENDİ giriş kaydı (Ayarlar → Güvenlik).
     *
     * <p>⚠️ Yönetim satırından (`LoginRow`) ayrı tutuldu: burada `username` ve
     * `userId` yoktur — kullanıcı zaten kendi kayıtlarına bakıyor, o alanları
     * göndermek gereksiz veri sızdırmaktır.
     */
    public record MyLoginRow(
            UUID id,
            String ip,
            String country,
            String device,
            String browser,
            String os,
            String surface,
            boolean success,
            Instant createdAt
    ) {
        public static MyLoginRow from(LoginEvent e) {
            return new MyLoginRow(e.getId(), e.getIp(), e.getCountry(), e.getDevice(),
                    e.getBrowser(), e.getOs(), e.getSurface(), e.isSuccess(), e.getCreatedAt());
        }
    }

    public record TrafficOverview(
            int days,
            long totalLogins,
            long uniqueUsers,
            long failedLogins,
            List<CountRow> byCountry,
            List<CountRow> byDevice,
            List<CountRow> byBrowser,
            List<DayCount> byDay,
            List<TopUserRow> topUsers
    ) {
    }
}
