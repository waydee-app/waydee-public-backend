package com.waydee.billing.application;

import com.waydee.billing.api.dto.InvoiceDtos.AccountingSummary;
import com.waydee.billing.api.dto.InvoiceDtos.BillingSummary;
import com.waydee.billing.api.dto.InvoiceDtos.CurrencyTotal;
import com.waydee.billing.api.dto.InvoiceDtos.InvoiceDetail;
import com.waydee.billing.api.dto.InvoiceDtos.InvoiceSummary;
import com.waydee.billing.api.dto.InvoiceDtos.LabelCount;
import com.waydee.billing.api.dto.InvoiceDtos.RevenueDay;
import com.waydee.billing.api.dto.InvoiceDtos.TopCustomer;
import com.waydee.billing.domain.Invoice;
import com.waydee.billing.domain.InvoiceStatus;
import com.waydee.billing.infrastructure.InvoiceRepository;
import com.waydee.common.error.ApiException;
import com.waydee.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Faturalandırma.
 *
 * Bir alan satın alındığı anda fatura kesilir (ödeme şu an mock olduğu için
 * doğrudan {@code PAID}). Fatura satın alma transaction'ının **içinde** yazılır:
 * satın alma geri alınırsa fatura da geri alınır, "faturası olmayan satın alma"
 * ya da tersi oluşmaz.
 *
 * Vergi: {@code waydee.billing.tax-rate} yüzdesi (varsayılan 0). Tahsil edilen
 * tutar **toplam** kabul edilir; oran verilirse ara toplam ve vergi bu toplamdan
 * geriye doğru ayrıştırılır (KDV dahil fiyat mantığı) — böylece fatura toplamı
 * her zaman gerçekte çekilen tutara eşittir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Value("${waydee.billing.tax-rate:0}")
    private BigDecimal taxRate;

    /**
     * Faturayı kesmek için gereken her şey — billing modülü territory/identity
     * modüllerine bağımlı olmasın diye veriler çağıran taraftan taşınır.
     */
    public record IssueInvoiceCommand(
            UUID userId,
            UUID territoryId,
            UUID purchaseId,
            String buyerUsername,
            String buyerName,
            String buyerEmail,
            String territoryName,
            String regionLabel,
            BigDecimal areaKm2,
            int radiusM,
            BigDecimal pricePerKm2,
            String currency,
            BigDecimal amountPaid,
            String paymentMethod,
            String paymentReference,
            /** PURCHASE | RENEWAL — ciroda ilk alım ve yenileme ayrı okunur. */
            String kind,
            Instant leaseStartedAt,
            Instant leaseExpiresAt,
            /** Uygulanan kupon kodu (yoksa null) — faturaya o günkü hâliyle kopyalanır. */
            String couponCode,
            /** İndirim tutarı (yoksa null). `amountPaid` zaten indirimli tutardır. */
            BigDecimal discountAmount
    ) {
        /** İlk alım için kısa kurucu (kiralama dönemi bölgeden okunur). */
        public IssueInvoiceCommand {
            if (kind == null || kind.isBlank()) {
                kind = "PURCHASE";
            }
        }
    }

    @Transactional
    public Invoice issue(IssueInvoiceCommand cmd) {
        // Idempotency: aynı satın alma iki kez faturalanmaz (DB'de de UNIQUE).
        if (cmd.purchaseId() != null && invoiceRepository.existsByPurchaseId(cmd.purchaseId())) {
            log.debug("Satın alma zaten faturalanmış: {}", cmd.purchaseId());
            return null;
        }
        BigDecimal total = cmd.amountPaid().setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = taxRate == null ? BigDecimal.ZERO : taxRate;
        BigDecimal subtotal = total;
        BigDecimal tax = BigDecimal.ZERO;
        if (rate.signum() > 0) {
            // Tahsil edilen tutar KDV dahildir: ara toplam = toplam / (1 + oran/100)
            BigDecimal divisor = BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            subtotal = total.divide(divisor, 2, RoundingMode.HALF_UP);
            tax = total.subtract(subtotal);
        }

        Invoice invoice = new Invoice(
                nextInvoiceNo(),
                cmd.userId(),
                cmd.territoryId(),
                cmd.purchaseId(),
                InvoiceStatus.PAID,
                cmd.buyerUsername(),
                cmd.buyerName(),
                cmd.buyerEmail(),
                ("RENEWAL".equals(cmd.kind()) ? "Kiralama yenileme — " : "Bölge kiralama — ")
                        + (cmd.territoryName() == null ? "Bölge" : cmd.territoryName()),
                cmd.regionLabel(),
                cmd.areaKm2(),
                cmd.radiusM(),
                cmd.pricePerKm2(),
                cmd.currency(),
                subtotal,
                rate,
                tax,
                total,
                cmd.paymentMethod(),
                cmd.paymentReference());
        invoice.applyLease(cmd.kind(), cmd.leaseStartedAt(), cmd.leaseExpiresAt());
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Fatura kesildi: {} → {} {} ({})", saved.getInvoiceNo(), total, cmd.currency(), cmd.buyerUsername());
        return saved;
    }

    /** WD-<yıl>-<6 hane>; sıra numarası DB dizisinden gelir (yarışsız). */
    private String nextInvoiceNo() {
        long seq = invoiceRepository.nextInvoiceNumber();
        int year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
        return "WD-%d-%06d".formatted(year, seq);
    }

    // ------------------------------------------------------------ kullanıcı

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> myInvoices(UUID userId, Pageable pageable) {
        // Projenin sayfa sözleşmesi PageResponse'tur; Spring'in ham Page'i dışarı sızmaz.
        return PageResponse.from(invoiceRepository.findByUserIdOrderByIssuedAtDesc(userId, pageable),
                InvoiceSummary::from);
    }

    @Transactional(readOnly = true)
    public InvoiceDetail myInvoice(UUID id, UUID userId) {
        // Yabancı faturaya erişim 404 döner: fatura kimliğinin varlığı sızmaz.
        return invoiceRepository.findByIdAndUserId(id, userId)
                .map(InvoiceDetail::from)
                .orElseThrow(() -> ApiException.notFound("Fatura bulunamadı"));
    }

    /** Profildeki "Faturalandırma" özeti — satır çekmeden (COUNT + SUM). */
    @Transactional(readOnly = true)
    public BillingSummary summary(UUID userId) {
        return new BillingSummary(
                invoiceRepository.countByUserId(userId),
                invoiceRepository.latestCurrency(userId).orElse("TRY"),
                invoiceRepository.sumTotalByUser(userId));
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> adminList(String query, Pageable pageable) {
        // Boş arama = tüm faturalar (LIKE '%%' her satırı tutar).
        return PageResponse.from(invoiceRepository.adminSearch(query == null ? "" : query.trim(), pageable),
                InvoiceSummary::from);
    }

    @Transactional(readOnly = true)
    public InvoiceDetail adminGet(UUID id) {
        return invoiceRepository.findById(id)
                .map(InvoiceDetail::from)
                .orElseThrow(() -> ApiException.notFound("Fatura bulunamadı"));
    }

    /**
     * Muhasebe panosu — dönem cirosu, KDV, para birimi kırılımı, günlük grafik,
     * en çok harcayanlar ve durum dağılımı. Hepsi toplu (GROUP BY) sorgularla;
     * fatura satırları belleğe çekilmez.
     */
    @Transactional(readOnly = true)
    public AccountingSummary accounting(int requestedDays) {
        int days = clampDays(requestedDays);
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(days));
        Instant prevSince = now.minus(Duration.ofDays(2L * days));

        List<CurrencyTotal> byCurrency = invoiceRepository.totalsByCurrency(since).stream()
                .map(r -> new CurrencyTotal((String) r[0], ((Number) r[1]).longValue(),
                        (BigDecimal) r[2], (BigDecimal) r[3], (BigDecimal) r[4]))
                .sorted(Comparator.comparing(CurrencyTotal::total).reversed())
                .toList();

        // Baskın para birimi = dönemde en çok ciro yapılan; toplamlar onun üzerinden okunur.
        CurrencyTotal primary = byCurrency.isEmpty() ? null : byCurrency.get(0);
        String currency = primary == null ? "TRY" : primary.currency();
        BigDecimal grand = primary == null ? BigDecimal.ZERO : primary.total();
        BigDecimal tax = primary == null ? BigDecimal.ZERO : primary.tax();
        long count = byCurrency.stream().mapToLong(CurrencyTotal::invoiceCount).sum();
        BigDecimal avg = primary == null || primary.invoiceCount() == 0
                ? BigDecimal.ZERO
                : primary.total().divide(BigDecimal.valueOf(primary.invoiceCount()), 2, RoundingMode.HALF_UP);

        // Önceki eşit dönem — trend oku için (aynı para biriminde).
        BigDecimal prevTotal = invoiceRepository.totalsByCurrency(prevSince).stream()
                .filter(r -> currency.equals(r[0]))
                .map(r -> (BigDecimal) r[4])
                .findFirst()
                .orElse(BigDecimal.ZERO)
                .subtract(grand); // prevSince..now toplamından bu dönemi çıkar
        if (prevTotal.signum() < 0) {
            prevTotal = BigDecimal.ZERO;
        }
        Double change = prevTotal.signum() == 0
                ? null
                : grand.subtract(prevTotal).multiply(BigDecimal.valueOf(100))
                        .divide(prevTotal, 1, RoundingMode.HALF_UP).doubleValue();

        List<RevenueDay> revenue = invoiceRepository.revenueByDay(since).stream()
                .map(r -> new RevenueDay(
                        toLocalDate(r[0]).toString(),
                        ((Number) r[1]).longValue(),
                        (BigDecimal) r[2]))
                .toList();

        List<TopCustomer> top = invoiceRepository.topCustomers(since, PageRequest.of(0, 8)).stream()
                .map(r -> new TopCustomer((UUID) r[0], (String) r[1], (String) r[2],
                        ((Number) r[3]).longValue(), (BigDecimal) r[4]))
                .toList();

        List<LabelCount> statuses = invoiceRepository.countByStatus(since).stream()
                .map(r -> new LabelCount(((InvoiceStatus) r[0]).name(), ((Number) r[1]).longValue()))
                .toList();

        return new AccountingSummary(days, count, currency, grand, tax, avg, prevTotal, change,
                byCurrency, fillGaps(revenue, days), top, statuses);
    }

    /**
     * `date_trunc` sonucunun Java tipi sürücü/sürüm değiştikçe farklı gelir
     * (Instant · Timestamp · OffsetDateTime). Tek bir cast'e güvenmek yerine
     * hepsini karşılayan bir dönüştürücü kullanılır — aksi hâlde ClassCastException.
     */
    private static LocalDate toLocalDate(Object value) {
        return switch (value) {
            case Instant instant -> instant.atZone(ZoneOffset.UTC).toLocalDate();
            case java.sql.Timestamp ts -> ts.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            case java.time.OffsetDateTime odt -> odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            case java.time.LocalDateTime ldt -> ldt.toLocalDate();
            case java.sql.Date d -> d.toLocalDate();
            default -> LocalDate.parse(value.toString().substring(0, 10));
        };
    }

    /** Grafikte boşluk kalmasın diye ciro olmayan günler 0 ile doldurulur. */
    private static List<RevenueDay> fillGaps(List<RevenueDay> rows, int days) {
        Map<String, RevenueDay> byDate = rows.stream()
                .collect(Collectors.toMap(RevenueDay::date, r -> r, (a, b) -> a));
        List<RevenueDay> out = new java.util.ArrayList<>(days);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            out.add(byDate.getOrDefault(date, new RevenueDay(date, 0, BigDecimal.ZERO)));
        }
        return out;
    }

    private static int clampDays(int days) {
        if (days <= 7) return 7;
        if (days <= 30) return 30;
        if (days <= 90) return 90;
        return 365;
    }
}
