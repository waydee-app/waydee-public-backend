package com.waydee.common.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 17 Ağu 2026 denetiminde kapatılan <b>hız sınırı atlatma</b> açığının
 * regresyon testi.
 *
 * <p>Açık şuydu: {@code X-Forwarded-For}'un <b>en solundaki</b> girdi okunuyordu
 * ve o girdiyi istemcinin kendisi yazabiliyor. Yük dengeleyici gerçek eşi
 * listeye <b>ekler</b>, silmez — dolayısıyla güvenilir olan taraf <b>sağdır</b>.
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String xff, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        return req;
    }

    @Test
    @DisplayName("Tek vekil: saldırganın uydurduğu sol girdi YOK SAYILIR")
    void picksRealClientBehindSingleProxy() {
        ClientIpResolver resolver = new ClientIpResolver(1, false);
        // Saldırgan "1.2.3.4" yazdı; ALB gerçek eşini (203.0.113.9) SAĞA ekledi.
        assertEquals("203.0.113.9",
                resolver.resolve(request("1.2.3.4, 203.0.113.9", "10.0.0.5")));
    }

    @Test
    @DisplayName("Sahte girdiler ne kadar çok olursa olsun sonuç değişmez")
    void spoofedChainCannotShiftTheResult() {
        ClientIpResolver resolver = new ClientIpResolver(1, false);
        assertEquals("203.0.113.9",
                resolver.resolve(request("9.9.9.9, 8.8.8.8, 7.7.7.7, 203.0.113.9", "10.0.0.5")));
    }

    @Test
    @DisplayName("Temiz istek: tek girdi zaten gerçek istemcidir")
    void singleEntryIsTheClient() {
        ClientIpResolver resolver = new ClientIpResolver(1, false);
        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "10.0.0.5")));
    }

    @Test
    @DisplayName("İki katmanlı kurulum (CDN → ALB) sağdan İKİNCİYİ seçer")
    void twoHops() {
        ClientIpResolver resolver = new ClientIpResolver(2, false);
        assertEquals("203.0.113.9",
                resolver.resolve(request("1.2.3.4, 203.0.113.9, 10.0.0.7", "10.0.0.5")));
    }

    @Test
    @DisplayName("Vekil yokken XFF hiç okunmaz")
    void ignoresHeaderWithoutTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(0, false);
        assertEquals("10.0.0.5", resolver.resolve(request("1.2.3.4", "10.0.0.5")));
        assertFalse(resolver.behindTrustedProxy());
    }

    @Test
    @DisplayName("Başlık yoksa doğrudan eşin adresine düşülür")
    void fallsBackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver(1, false);
        assertEquals("10.0.0.5", resolver.resolve(request(null, "10.0.0.5")));
    }

    @Test
    @DisplayName("Zincir beklenenden kısaysa en sola düşülür, indeks taşmaz")
    void shortChainDoesNotOverflow() {
        ClientIpResolver resolver = new ClientIpResolver(3, false);
        assertEquals("1.2.3.4", resolver.resolve(request("1.2.3.4", "10.0.0.5")));
    }

    @Test
    @DisplayName("Eski trust-forwarded bayrağı 1 sıçramaya karşılık gelir (geriye dönük uyum)")
    void legacyFlagMapsToOneHop() {
        ClientIpResolver resolver = new ClientIpResolver(-1, true);
        assertTrue(resolver.behindTrustedProxy());
        assertEquals("203.0.113.9",
                resolver.resolve(request("1.2.3.4, 203.0.113.9", "10.0.0.5")));
    }

    @Test
    @DisplayName("Vekil yokken CDN ülke başlığı OKUNMAZ (istemci uydurabilir)")
    void geoHeaderIgnoredWithoutTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(0, false);
        MockHttpServletRequest req = request(null, "10.0.0.5");
        req.addHeader("CF-IPCountry", "US");
        assertNull(resolver.trustedHeader(req, "CF-IPCountry"));
    }

    @Test
    @DisplayName("Vekil arkasındayken ülke başlığı okunur")
    void geoHeaderReadBehindTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(1, false);
        MockHttpServletRequest req = request("203.0.113.9", "10.0.0.5");
        req.addHeader("CF-IPCountry", "TR");
        assertEquals("TR", resolver.trustedHeader(req, "CF-IPCountry"));
    }
}
