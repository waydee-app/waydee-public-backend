package com.waydee.billing.api.dto;

import com.waydee.billing.domain.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /** Liste satırı — fatura listesinde gösterilen özet. */
    public record InvoiceSummary(
            UUID id,
            String invoiceNo,
            Instant issuedAt,
            String status,
            String description,
            String currency,
            BigDecimal total,
            UUID territoryId
    ) {
        public static InvoiceSummary from(Invoice i) {
            return new InvoiceSummary(i.getId(), i.getInvoiceNo(), i.getIssuedAt(), i.getStatus().name(),
                    i.getDescription(), i.getCurrency(), i.getTotal(), i.getTerritoryId());
        }
    }

    /** Tam fatura — yazdırılabilir detay ekranı. */
    public record InvoiceDetail(
            UUID id,
            String invoiceNo,
            Instant issuedAt,
            String status,

            String buyerUsername,
            String buyerName,
            String buyerEmail,

            String description,
            String regionLabel,
            BigDecimal areaKm2,
            int radiusM,
            BigDecimal pricePerKm2,

            String currency,
            BigDecimal subtotal,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal total,

            String paymentMethod,
            String paymentReference,
            UUID territoryId
    ) {
        public static InvoiceDetail from(Invoice i) {
            return new InvoiceDetail(
                    i.getId(), i.getInvoiceNo(), i.getIssuedAt(), i.getStatus().name(),
                    i.getBuyerUsername(), i.getBuyerName(), i.getBuyerEmail(),
                    i.getDescription(), i.getRegionLabel(), i.getAreaKm2(), i.getRadiusM(), i.getPricePerKm2(),
                    i.getCurrency(), i.getSubtotal(), i.getTaxRate(), i.getTaxAmount(), i.getTotal(),
                    i.getPaymentMethod(), i.getPaymentReference(), i.getTerritoryId());
        }
    }

    /** Kullanıcının fatura özeti (profildeki "Faturalandırma" başlığı). */
    public record BillingSummary(
            long invoiceCount,
            String currency,
            BigDecimal totalSpent
    ) {
    }

    // ------------------------------------------------------- muhasebe (admin)

    /** Para birimi bazında dönem toplamı. */
    public record CurrencyTotal(
            String currency,
            long invoiceCount,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal total
    ) {
    }

    /** Gün bazında ciro (grafik). */
    public record RevenueDay(String date, long invoiceCount, BigDecimal total) {
    }

    /** En çok harcayan kullanıcı. */
    public record TopCustomer(
            UUID userId,
            String username,
            String displayName,
            long invoiceCount,
            BigDecimal total
    ) {
    }

    /**
     * Yönetim muhasebe panosu.
     *
     * @param days           dönem (gün)
     * @param totalsByCurrency para birimi başına ara toplam / KDV / toplam
     * @param statusCounts   duruma göre fatura adedi (PAID, ISSUED, CANCELLED…)
     * @param avgInvoice     ortalama fatura tutarı (baskın para biriminde)
     */
    public record AccountingSummary(
            int days,
            long invoiceCount,
            String primaryCurrency,
            BigDecimal grandTotal,
            BigDecimal totalTax,
            BigDecimal avgInvoice,
            BigDecimal prevPeriodTotal,
            Double changePercent,
            List<CurrencyTotal> totalsByCurrency,
            List<RevenueDay> revenueByDay,
            List<TopCustomer> topCustomers,
            List<LabelCount> statusCounts
    ) {
    }

    public record LabelCount(String label, long count) {
    }
}
