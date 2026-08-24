package com.waydee.traffic.application;

import com.waydee.common.web.PageResponse;
import com.waydee.traffic.api.dto.TrafficDtos.CountRow;
import com.waydee.traffic.api.dto.TrafficDtos.DayCount;
import com.waydee.traffic.api.dto.TrafficDtos.LoginRow;
import com.waydee.traffic.api.dto.TrafficDtos.MyLoginRow;
import com.waydee.traffic.api.dto.TrafficDtos.TopUserRow;
import com.waydee.traffic.api.dto.TrafficDtos.TrafficOverview;
import com.waydee.traffic.domain.LoginEvent;
import com.waydee.traffic.infrastructure.LoginEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Trafik (giriş) kayıtları ve admin raporları.
 * Kayıt **async**: giriş yolunu yavaşlatmaz, hata olsa bile girişi bozmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficService {

    private final LoginEventRepository repository;

    @Async("analyticsExecutor")
    @Transactional
    public void record(UUID userId, String username, String ip, String country, String device,
                       String browser, String os, String surface, boolean success, String userAgent) {
        try {
            repository.save(new LoginEvent(userId, username, ip, country, device, browser, os,
                    surface, success, userAgent));
        } catch (Exception ex) {
            log.warn("Giriş kaydı yazılamadı: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public TrafficOverview overview(int days) {
        int window = Math.min(Math.max(days, 1), 365);
        Instant since = Instant.now().minus(Duration.ofDays(window));

        List<CountRow> byCountry = repository.countByCountry(since).stream()
                .map(r -> new CountRow((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .toList();
        List<CountRow> byDevice = repository.countByDevice(since).stream()
                .map(r -> new CountRow((String) r[0], ((Number) r[1]).longValue(), 0))
                .toList();
        List<CountRow> byBrowser = repository.countByBrowser(since).stream()
                .map(r -> new CountRow((String) r[0], ((Number) r[1]).longValue(), 0))
                .toList();
        List<DayCount> byDay = fillDays(repository.countByDay(since), window);
        List<TopUserRow> topUsers = repository.topUsers(since, PageRequest.of(0, 10)).stream()
                .map(r -> new TopUserRow((String) r[0], ((Number) r[1]).longValue(),
                        toInstant(r[2]), ((Number) r[3]).longValue()))
                .toList();

        return new TrafficOverview(
                window,
                repository.countByCreatedAtAfter(since),
                repository.countDistinctUsers(since),
                repository.countByCreatedAtAfterAndSuccessFalse(since),
                byCountry, byDevice, byBrowser, byDay, topUsers);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginRow> logins(UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var result = userId != null
                ? repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : repository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.from(result, LoginRow::from);
    }

    /**
     * Oturum sahibinin KENDİ giriş geçmişi ("bu hesaba nereden girildi?").
     *
     * <p>⚠️ Kimlik yönetimin değil <b>kullanıcının</b> ucu: userId parametre
     * olarak alınmaz, oturumdan gelir — aksi halde herkes başkasının giriş
     * kayıtlarını (IP dahil) okuyabilirdi. Yönetim ucu
     * ({@code /admin/traffic/logins}) ayrı durur ve ADMIN ister.
     *
     * <p>Başarısız denemeler de dönülür: hesabına birinin girmeye çalıştığını
     * görmek kullanıcının hakkı ve şüpheli erişimin ilk işareti.
     */
    @Transactional(readOnly = true)
    public PageResponse<MyLoginRow> myLogins(UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 50));
        return PageResponse.from(
                repository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                MyLoginRow::from);
    }

    /** Boş günleri 0 ile doldurur (grafik sürekli olsun). */
    private List<DayCount> fillDays(List<Object[]> rows, int window) {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (Object[] row : rows) {
            Instant day = toInstant(row[0]);
            if (day == null) {
                continue;
            }
            map.put(day.atZone(ZoneOffset.UTC).toLocalDate().toString(), ((Number) row[1]).longValue());
        }
        List<DayCount> out = new java.util.ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = window - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            out.add(new DayCount(date, map.getOrDefault(date, 0L)));
        }
        return out;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return null;
    }
}
