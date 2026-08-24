package com.waydee.billing.infrastructure;

import com.waydee.billing.domain.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByUserIdOrderByIssuedAtDesc(UUID userId, Pageable pageable);

    Optional<Invoice> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByPurchaseId(UUID purchaseId);

    long countByUserId(UUID userId);

    /** Kullanıcının iptal edilmemiş faturalarının toplamı — satır çekmeden (SUM). */
    @Query("""
            select coalesce(sum(i.total), 0) from Invoice i
            where i.userId = :userId and i.status <> com.waydee.billing.domain.InvoiceStatus.CANCELLED
            """)
    java.math.BigDecimal sumTotalByUser(@Param("userId") UUID userId);

    /** Özetteki para birimi — kullanıcının en son faturasınınki. */
    @Query("select i.currency from Invoice i where i.userId = :userId order by i.issuedAt desc limit 1")
    Optional<String> latestCurrency(@Param("userId") UUID userId);

    /**
     * Sıradaki fatura numarası — DB dizisi (sequence) tek kaynak; eşzamanlı satın
     * almalarda bile numara tekrarlamaz.
     */
    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceNumber();

    /**
     * Admin araması: fatura no, alıcı kullanıcı adı ya da alıcı adı.
     * ⚠️ Boş arama için NULL değil **boş string** geçilir — tipsiz null parametre
     * PostgreSQL'de `lower(bytea)` hatası verir (bkz. vault 12-tuzaklar).
     */
    @Query("""
            select i from Invoice i
            where lower(i.invoiceNo) like lower(concat('%', :query, '%'))
               or lower(i.buyerUsername) like lower(concat('%', :query, '%'))
               or lower(i.buyerName) like lower(concat('%', :query, '%'))
            """)
    Page<Invoice> adminSearch(@Param("query") String query, Pageable pageable);

    // ------------------------------------------------------- muhasebe (admin)

    /** Para birimi bazında toplam: `[currency, adet, ara toplam, KDV, toplam]`. */
    @Query("""
            select i.currency, count(i), coalesce(sum(i.subtotal), 0),
                   coalesce(sum(i.taxAmount), 0), coalesce(sum(i.total), 0)
            from Invoice i
            where i.status <> com.waydee.billing.domain.InvoiceStatus.CANCELLED
              and i.issuedAt >= :since
            group by i.currency
            """)
    List<Object[]> totalsByCurrency(@Param("since") java.time.Instant since);

    /** Gün bazında ciro: `[gün, adet, toplam]` — grafik için. */
    @Query(value = """
            select date_trunc('day', issued_at) as d, count(*), coalesce(sum(total), 0)
            from invoices
            where status <> 'CANCELLED' and issued_at >= :since
            group by d order by d
            """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("since") java.time.Instant since);

    /** En çok harcayan kullanıcılar: `[userId, kullanıcı adı, ad, adet, toplam]`. */
    @Query("""
            select i.userId, i.buyerUsername, i.buyerName, count(i), coalesce(sum(i.total), 0)
            from Invoice i
            where i.status <> com.waydee.billing.domain.InvoiceStatus.CANCELLED
              and i.issuedAt >= :since
            group by i.userId, i.buyerUsername, i.buyerName
            order by sum(i.total) desc
            """)
    List<Object[]> topCustomers(@Param("since") java.time.Instant since, Pageable pageable);

    /** Duruma göre fatura adedi (tahsilat panosu). */
    @Query("select i.status, count(i) from Invoice i where i.issuedAt >= :since group by i.status")
    List<Object[]> countByStatus(@Param("since") java.time.Instant since);

    /** Dönem içindeki tüm faturalar — CSV dışa aktarım için (sınırlı). */
    @Query("select i from Invoice i where i.issuedAt >= :since order by i.issuedAt desc")
    List<Invoice> exportSince(@Param("since") java.time.Instant since, Pageable pageable);
}
