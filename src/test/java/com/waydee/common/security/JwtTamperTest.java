package com.waydee.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Token kurcalama savunması — regresyon bekçisi</b> (19 Ağu 2026).
 *
 * <h3>🔴 Neden bu test var</h3>
 * <p>Kullanıcı, bir güvenlik incelemesinin ardından *"DevTools'ta rolü
 * değiştirip işlem yapılabiliyor"* endişesini iletti. Arayüz tarafındaki açık
 * gerçekti (rol {@code localStorage}'daki nesneden okunuyordu) ve kapatıldı;
 * ama asıl soru şuydu: <b>sunucu kurcalanmış bir token'ı gerçekten reddediyor
 * mu?</b>
 *
 * <p>Bu testler o cevabı <b>kanıta</b> çevirir. Bir gün biri
 * {@code parseSignedClaims}'i {@code parseClaimsJwt} ile değiştirirse ya da
 * imza doğrulamasını gevşetirse, <b>derleme kırılır</b>. Güvenlik özelliği
 * yorum satırıyla değil, testle korunur.
 */
class JwtTamperTest {

    /** Test anahtarı — üretimdeki sırla ilgisi yok. */
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "waydee-test-secret-key-that-is-long-enough-for-hs512-algorithm-0123456789"
                    .getBytes(StandardCharsets.UTF_8));

    private final JwtService jwt = new JwtService(
            new JwtProperties(SECRET, "waydee", Duration.ofMinutes(30), Duration.ofDays(30)));

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Geçerli token çözülür ve roldeki değer AYNEN gelir")
    void gecerliToken() {
        String token = jwt.generateAccessToken(userId, "musty", "USER");
        var parsed = jwt.parse(token);
        assertTrue(parsed.isPresent());
        assertEquals(userId, parsed.get().id());
        assertEquals("USER", parsed.get().role());
    }

    /**
     * 🔴 <b>ASIL TEST.</b> Saldırganın en kolay denemesi: token'ın gövdesindeki
     * {@code "role":"USER"} kısmını {@code "ADMIN"} yapıp aynı imzayı bırakmak.
     */
    @Test
    @DisplayName("Gövdesi ADMIN'e çevrilen token REDDEDİLİR (imza tutmaz)")
    void govdesiDegistirilmisTokenReddedilir() {
        String token = jwt.generateAccessToken(userId, "musty", "USER");
        String[] parts = token.split("\\.");

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"role\":\"USER\""), "Test kurgusu bozuk: rol gövdede yok");

        String tampered = payload.replace("\"role\":\"USER\"", "\"role\":\"ADMIN\"");
        String rebuilt = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(tampered.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertTrue(jwt.parse(rebuilt).isEmpty(),
                "Kurcalanmış token KABUL EDİLDİ — imza doğrulaması devre dışı kalmış olmalı");
    }

    /**
     * 🔴 <b>`alg: none` saldırısı.</b> Klasik JWT açığı: imzayı tamamen atıp
     * algoritmayı {@code none} yapmak. Kütüphane bunu kabul ederse rol
     * serbestçe yazılabilir.
     */
    @Test
    @DisplayName("İmzasız (alg=none) token REDDEDİLİR")
    void imzasizTokenReddedilir() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + userId + "\",\"role\":\"ADMIN\",\"iss\":\"waydee\","
                        + "\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}")
                        .getBytes(StandardCharsets.UTF_8));

        assertTrue(jwt.parse(header + "." + body + ".").isEmpty(),
                "alg=none token KABUL EDİLDİ — kritik açık");
    }

    /** Başka bir anahtarla imzalanmış token — sırrı bilmeyen saldırgan. */
    @Test
    @DisplayName("Başka anahtarla imzalanmış token REDDEDİLİR")
    void yabanciAnahtarReddedilir() {
        SecretKey foreign = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                Base64.getEncoder().encodeToString(
                        "bambaska-bir-anahtar-yeterince-uzun-olmali-hs512-icin-0123456789abcd"
                                .getBytes(StandardCharsets.UTF_8))));
        String forged = Jwts.builder()
                .subject(userId.toString())
                .claim("username", "musty")
                .claim("role", "ADMIN")
                .issuer("waydee")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(foreign, Jwts.SIG.HS512)
                .compact();

        assertTrue(jwt.parse(forged).isEmpty(), "Yabancı anahtarla imzalı token KABUL EDİLDİ");
    }

    /**
     * ⚠️ {@code issuer} kontrolü de bir savunmadır: aynı anahtarı paylaşan
     * başka bir servisin token'ı buraya geçmemeli.
     */
    @Test
    @DisplayName("Yanlış issuer REDDEDİLİR")
    void yanlisIssuerReddedilir() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String other = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "ADMIN")
                .issuer("baska-servis")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        assertTrue(jwt.parse(other).isEmpty(), "Yabancı issuer KABUL EDİLDİ");
    }

    @Test
    @DisplayName("Süresi dolmuş token REDDEDİLİR")
    void suresiDolmusReddedilir() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String expired = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "USER")
                .issuer("waydee")
                .expiration(Date.from(Instant.now().minus(Duration.ofMinutes(5))))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        assertTrue(jwt.parse(expired).isEmpty(), "Süresi dolmuş token KABUL EDİLDİ");
    }
}
