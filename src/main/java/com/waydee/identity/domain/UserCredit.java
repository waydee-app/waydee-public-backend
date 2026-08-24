package com.waydee.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * <b>Yapay zekâ kredi bakiyesi</b> (V45) — kullanıcı başına tek satır.
 *
 * <p>Kredi, görsel üretiminin para birimidir: plan yenilendiğinde yüklenir,
 * her üretimde düşer, üretim başarısız olursa iade edilir.
 *
 * <p>🔴 <b>Bakiye burada, geçmiş {@link CreditLedgerEntry}'de.</b> Tek tabloyla
 * yetinmek "şu an kaç?" sorusunu yanıtlar ama "neden bu kadar?" sorusunu
 * yanıtsız bırakırdı — para benzeri bir sayaçta bu, destek taleplerini
 * çözülemez hâle getirir.
 *
 * <p>⚠️ <b>Bakiye ASLA bu sınıftaki bir setter ile düşürülmez.</b> Harcama
 * {@code UserCreditRepository.tryDebit} içindeki <b>koşullu tek UPDATE</b> ile
 * yapılır. Buradan okuyup buradan yazmak, iki eşzamanlı isteğin aynı bakiyeyi
 * okuyup ikisinin de harcamasına izin verirdi (kredi ekonomisindeki en klasik
 * açık). Bu sınıftaki metotlar yalnız <b>yükleme/iade</b> içindir; ikisi de
 * bakiyeyi <b>artırır</b>, yani yarış durumunda kullanıcı aleyhine bir sonuç
 * doğurmaz.
 */
@Entity
@Table(name = "user_credits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCredit {

    /** Kullanıcının kimliği <b>birincil anahtardır</b> — ayrı bir id gereksiz. */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "balance", nullable = false)
    private int balance;

    @Column(name = "granted_total", nullable = false)
    private long grantedTotal;

    @Column(name = "spent_total", nullable = false)
    private long spentTotal;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserCredit(UUID userId) {
        this.userId = userId;
        this.balance = 0;
        this.grantedTotal = 0;
        this.spentTotal = 0;
        this.updatedAt = Instant.now();
    }

    /**
     * <b>Dönemlik hak yüklemesi</b> — bakiye pakete <b>eşitlenir</b>, üstüne
     * eklenmez.
     *
     * <p>🔴 Bu bilinçli bir ekonomi kararıdır. Birikimli olsaydı yıllık bir
     * Premium üyelik 120.000 kredilik bir stok yapar, üyeliğini bir ay sonra
     * bıraksa bile elinde yıllarca yetecek bir hak kalırdı. "Paket" demek,
     * <b>o dönem için</b> demektir.
     *
     * @return bu yüklemenin bakiyeye net etkisi (defterdeki {@code delta})
     */
    public int grantPackage(int amount) {
        int delta = amount - balance;
        this.balance = amount;
        if (delta > 0) {
            this.grantedTotal += delta;
        }
        this.updatedAt = Instant.now();
        return delta;
    }

    /** Doğrudan ekleme/çıkarma — <b>yönetici düzeltmesi</b> (telafi, destek). */
    public void add(int amount) {
        this.balance += amount;
        if (amount > 0) {
            this.grantedTotal += amount;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * <b>İade</b> — başarısız bir üretimin kredisi geri verilir.
     *
     * <p>⚠️ {@link #add} DEĞİL: iade yeni bir hak değildir. {@code add}
     * kullanılsaydı "ömür boyu yüklenen" toplamı şişer ve iade edilen her
     * kredi ikinci kez verilmiş gibi görünürdü. Doğrusu <b>harcamayı geri
     * almaktır</b>.
     *
     * <p>⚠️ İadenin <b>tek sefer</b> olması burada değil, defterdeki
     * {@code ref_key} tekil kısıtında güvence altındadır.
     */
    public void refund(int amount) {
        this.balance += amount;
        this.spentTotal = Math.max(0, this.spentTotal - amount);
        this.updatedAt = Instant.now();
    }
}
