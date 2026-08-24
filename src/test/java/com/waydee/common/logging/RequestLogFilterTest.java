package com.waydee.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.waydee.common.net.ClientIpResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Erişim logu davranışı</b> (20 Ağu 2026).
 *
 * <p>🔴 Asıl korunan şey {@link #mdcHavuzaSizmaz()}: MDC temizlenmezse bir
 * sonraki isteğin log satırlarına <b>önceki kullanıcının kimliği</b> yapışır.
 * Sessiz, fark edilmesi zor ve gerçek bir veri karışması — testle çivilendi.
 */
class RequestLogFilterTest {

    private RequestLogFilter filter;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // (configuredHops, trustForwarded) — 0 hop: doğrudan remoteAddr okunur
        ClientIpResolver ip = new ClientIpResolver(0, false);
        filter = new RequestLogFilter(ip);
        ReflectionTestUtils.setField(filter, "slowMs", 500L);

        logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    private MockHttpServletRequest req(String method, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setRemoteAddr("203.0.113.9");
        return r;
    }

    private List<ILoggingEvent> run(MockHttpServletRequest request, int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (rq, rs) -> ((MockHttpServletResponse) rs).setStatus(status);
        filter.doFilter(request, response, chain);
        return appender.list;
    }

    @Test
    @DisplayName("Normal istek TEK satır INFO yazar")
    void tekSatirInfo() throws Exception {
        var events = run(req("GET", "/api/v1/posts"), 200);
        assertEquals(1, events.size(), "İstek başına tam bir satır olmalı");
        assertEquals(Level.INFO, events.get(0).getLevel());
        assertTrue(events.get(0).getFormattedMessage().contains("/api/v1/posts"));
    }

    @Test
    @DisplayName("5xx ERROR, 4xx WARN olur")
    void seviyeSonucaGore() throws Exception {
        run(req("GET", "/api/v1/a"), 500);
        run(req("GET", "/api/v1/b"), 404);
        assertEquals(Level.ERROR, appender.list.get(0).getLevel());
        assertEquals(Level.WARN, appender.list.get(1).getLevel());
    }

    @Test
    @DisplayName("Sağlık kontrolü ve statik varlıklar LOGLANMAZ")
    void gurultuElenir() throws Exception {
        run(req("GET", "/actuator/health"), 200);
        run(req("GET", "/favicon.ico"), 200);
        run(req("GET", "/assets/index-abc.js"), 200);
        assertTrue(appender.list.isEmpty(),
                "Sağlık kontrolü/statik dosya loglandı — canlıda gürültünün ana kaynağı buydu");
    }

    @Test
    @DisplayName("Yanıt X-Request-Id taşır ve her istekte FARKLIDIR")
    void requestIdUretilir() throws Exception {
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        FilterChain noop = (rq, rs) -> { };
        filter.doFilter(req("GET", "/api/v1/x"), r1, noop);
        filter.doFilter(req("GET", "/api/v1/x"), r2, noop);

        String a = r1.getHeader(RequestLogFilter.REQUEST_ID_HEADER);
        String b = r2.getHeader(RequestLogFilter.REQUEST_ID_HEADER);
        assertNotNull(a);
        assertNotNull(b);
        assertFalse(a.equals(b), "Her istek kendi kimliğini almalı");
    }

    /**
     * 🔴 İstemcinin gönderdiği kimlik <b>kabul edilmez</b>: kabul etmek,
     * saldırganın kendi satırlarını başkasının isteğiyle aynı kimliğe
     * yazdırmasına izin verirdi (log injection).
     */
    @Test
    @DisplayName("İstemcinin gönderdiği X-Request-Id KULLANILMAZ")
    void istemciKimligiReddedilir() throws Exception {
        MockHttpServletRequest request = req("GET", "/api/v1/x");
        request.addHeader(RequestLogFilter.REQUEST_ID_HEADER, "saldirganin-kimligi");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (rq, rs) -> { });

        assertFalse("saldirganin-kimligi"
                .equals(response.getHeader(RequestLogFilter.REQUEST_ID_HEADER)));
    }

    /**
     * 🔴🔴 <b>REGRESYON BEKÇİSİ — ÜRETİMDE YAŞANDI.</b>
     *
     * <p>İlk sürümde {@code userId} log satırlarına <b>hiç girmedi</b>. Sebep:
     * bu filtre güvenlik zincirinin DIŞINDA ve zincir {@code SecurityContext}'i
     * kendi {@code finally}'sinde temizliyor — bizimkinden ÖNCE. Yani kimliği
     * "sonda okumak" hiçbir zaman işe yaramadı.
     *
     * <p>Doğru akış: kimliği zincirin <b>içinde</b>
     * ({@code JwtAuthenticationFilter}) MDC'ye koymak. Bu test o akışı taklit
     * eder: zincir MDC'yi doldurur, log satırı onu <b>taşımalıdır</b>.
     */
    @Test
    @DisplayName("Zincir içinde konan userId LOG SATIRINA girer")
    void zincirdeKonanKimlikSatiraGirer() throws Exception {
        UUID id = UUID.randomUUID();
        // JwtAuthenticationFilter'ın yaptığının aynısı:
        FilterChain kimlikKoyan = (rq, rs) ->
                MDC.put(RequestLogFilter.MDC_USER_ID, id.toString());

        filter.doFilter(req("GET", "/api/v1/x"), new MockHttpServletResponse(), kimlikKoyan);

        assertEquals(1, appender.list.size());
        assertEquals(id.toString(),
                appender.list.get(0).getMDCPropertyMap().get(RequestLogFilter.MDC_USER_ID),
                "userId log satırına GİRMEDİ — üretimde yaşanan hatanın aynısı");
    }

    /**
     * 🔴🔴 Tomcat iş parçacıklarını havuzdan verir. MDC temizlenmezse bir
     * sonraki isteğin satırlarına önceki kullanıcının kimliği yapışır.
     */
    @Test
    @DisplayName("MDC istek sonunda TEMİZLENİR — havuzdaki iş parçacığına sızmaz")
    void mdcHavuzaSizmaz() throws Exception {
        UUID id = UUID.randomUUID();
        FilterChain kimlikKoyan = (rq, rs) ->
                MDC.put(RequestLogFilter.MDC_USER_ID, id.toString());

        filter.doFilter(req("GET", "/api/v1/x"), new MockHttpServletResponse(), kimlikKoyan);

        assertNull(MDC.get(RequestLogFilter.MDC_USER_ID), "userId MDC'de KALDI — sızıntı");
        assertNull(MDC.get(RequestLogFilter.MDC_REQUEST_ID), "requestId MDC'de KALDI");
        assertNull(MDC.get(RequestLogFilter.MDC_IP), "ip MDC'de KALDI");
    }

    /** Zincir patlasa bile MDC temizlenmeli — `finally` gerçekten çalışıyor mu. */
    @Test
    @DisplayName("Zincir HATA atsa bile MDC temizlenir")
    void hatadaDaTemizlenir() {
        FilterChain patlayan = (rq, rs) -> { throw new IllegalStateException("patladı"); };
        try {
            filter.doFilter(req("GET", "/api/v1/x"), new MockHttpServletResponse(), patlayan);
        } catch (Exception ignored) {
            // beklenen
        }
        assertNull(MDC.get(RequestLogFilter.MDC_REQUEST_ID), "Hata yolunda MDC sızdı");
    }

    /**
     * ⚠️ Sorgu dizesi loglanmaz: arama terimleri ve jetonlar oraya düşer.
     */
    @Test
    @DisplayName("Sorgu dizesi log satırına GİRMEZ")
    void sorguDizesiLoglanmaz() throws Exception {
        MockHttpServletRequest request = req("GET", "/api/v1/search");
        request.setQueryString("q=gizli-arama&token=SIRR");
        run(request, 200);
        String line = appender.list.get(0).getFormattedMessage();
        assertFalse(line.contains("SIRR"), "Jeton log satırına sızdı");
        assertFalse(line.contains("gizli-arama"), "Arama terimi log satırına sızdı");
    }
}
