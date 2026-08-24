package com.waydee.common.net;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <b>İstemcinin GERÇEK IP'si</b> — tek kaynak.
 *
 * <h3>🔴 17 Ağu 2026 — DENETİM BULGUSU: hız sınırı header ile atlanabiliyordu</h3>
 *
 * <p>Eski kod {@code X-Forwarded-For}'un <b>EN SOLDAKİ</b> girdisini okuyordu.
 * Bu girdi <b>istemcinin kendi yazdığı</b> değerdir: yük dengeleyici (ALB /
 * Cloudflare) gerçek eşi listeye <b>EKLER</b>, var olanı silmez. Yani
 * {@code X-Forwarded-For: 1.2.3.4} yazan bir saldırgan her istekte farklı bir
 * sahte IP göndererek:
 * <ul>
 *   <li>{@code RateLimitFilter}'ın kovasını her seferinde <b>sıfırlıyor</b>
 *       (auth 15/dk ve API 300/dk sınırları tamamen etkisiz kalıyordu),</li>
 *   <li>denetim kaydına ({@code audit_logs}, {@code login_events}) <b>istediği
 *       IP'yi</b> yazdırabiliyordu — bir olay incelemesinde kayıtlar yanıltıcı
 *       olurdu,</li>
 *   <li>tıklama ölçümündeki 60 saniyelik tekrar bastırmayı atlayıp sayacı
 *       istediği yere çekebiliyordu.</li>
 * </ul>
 *
 * <p>⚠️ Eski {@code waydee.rate-limit.trust-forwarded} bayrağı bu açığı
 * <b>kapatmıyordu</b>: kapalıyken {@code getRemoteAddr()}'a düşülüyor, ama
 * {@code server.forward-headers-strategy: framework} Spring'in
 * {@code ForwardedHeaderFilter}'ını devreye sokuyor ve o filtre
 * {@code getRemoteAddr()}'ı <b>zaten aynı soldaki XFF girdisiyle</b> eziyor.
 * Yani iki yol da aynı sahte değere çıkıyordu.
 *
 * <h3>Doğrusu: SAĞDAN say</h3>
 * <p>Zincirin <b>sağ</b> ucundaki girdileri bizim güvendiğimiz vekiller ekler;
 * saldırgan oraya bir şey yazamaz (yazdığı her şey soluna itilir).
 * {@code trusted-proxy-hops} kaç vekil olduğunu söyler:
 *
 * <pre>
 *   X-Forwarded-For: &lt;saldırganın uydurduğu&gt;, &lt;GERÇEK istemci&gt;
 *                                                  ▲ hops=1 bunu seçer
 * </pre>
 *
 * <p>Değeri <b>dağıtıma göre</b> verilir: doğrudan ALB arkasında <b>1</b>,
 * Cloudflare→ALB gibi iki katmanlı bir kurulumda <b>2</b>. Sıfır (yerel
 * geliştirme) XFF'i tamamen yok sayar.
 */
@Component
public class ClientIpResolver {

    private static final String XFF = "X-Forwarded-For";

    private final int hops;

    /**
     * @param configuredHops  {@code waydee.security.trusted-proxy-hops}; verilmezse
     *                        (-1) eski {@code trust-forwarded} bayrağından türetilir,
     *                        böylece mevcut üretim ortamı env değişikliği olmadan
     *                        doğru davranışa geçer.
     * @param trustForwarded  eski bayrak (geriye dönük uyumluluk).
     */
    public ClientIpResolver(
            @Value("${waydee.security.trusted-proxy-hops:-1}") int configuredHops,
            @Value("${waydee.rate-limit.trust-forwarded:false}") boolean trustForwarded) {
        this.hops = configuredHops >= 0 ? configuredHops : (trustForwarded ? 1 : 0);
    }

    /** Güvenilir bir vekilin arkasında mıyız — vekilin eklediği başlıklara (ülke vb.) ancak o zaman güvenilir. */
    public boolean behindTrustedProxy() {
        return hops > 0;
    }

    /**
     * İstemcinin gerçek IP'si. Vekil yoksa doğrudan eşin adresi.
     *
     * <p>⚠️ Zincir beklenenden kısaysa (örn. sağlık kontrolü ALB'yi atlayıp
     * doğrudan geldiyse) en soldaki girdiye düşülür — sahte olabilir ama
     * o istek zaten güvenilir vekilden geçmemiştir ve daha iyi bir kaynak yoktur.
     */
    public String resolve(HttpServletRequest request) {
        if (hops > 0) {
            String forwarded = request.getHeader(XFF);
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                int index = Math.max(0, parts.length - hops);
                for (int i = index; i < parts.length; i++) {
                    String candidate = parts[i].trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    /**
     * Vekilin eklediği bir başlığı okur; vekil yoksa <b>null</b> döner.
     *
     * <p>🔒 {@code CF-IPCountry} gibi başlıkları koşulsuz okumak, herhangi bir
     * ziyaretçinin ülke istatistiğini istediği gibi doldurması demekti.
     */
    public String trustedHeader(HttpServletRequest request, String name) {
        return behindTrustedProxy() ? request.getHeader(name) : null;
    }
}
