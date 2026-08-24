package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.persistence.AppSetting;
import com.waydee.common.persistence.AppSettingRepository;
import com.waydee.identity.domain.BillingPeriod;
import com.waydee.identity.domain.UserPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <b>Üyelik fiyat tablosu</b> — çalışma zamanında, yönetim panelinden değişir.
 *
 * <p>🔴 <b>Neden DB'de:</b> fiyat eskiden yalnız {@code waydee.payment.pro-price}
 * ortam değişkeninden geliyordu; değiştirmek <b>yeniden dağıtım</b> gerektiriyordu.
 * Üstelik tanıtım sayfası fiyatı <b>koda gömülüydü</b> — yani ortam değişkeni
 * değiştirilse bile vitrin eski fiyatı göstermeye devam ederdi. Tek kaynak
 * burasıdır: ödeme oturumu, tanıtım sayfası ve plan ekranı aynı değeri okur.
 *
 * <p>🔴 <b>V37: tablo dört hücreye çıktı</b> — {plan} × {dönem}. Tek fiyat
 * yerine dört anahtar tutulur; "yıllık, aylığın %X'i" gibi bir çarpanla türetmek
 * yöneticinin iki fiyatı bağımsız belirlemesini imkânsız kılardı.
 *
 * <p>⚠️ Saklanan değer <b>her zaman aylık eşdeğerdir</b> — yıllık hücrede de.
 * Tahsil edilen tutarı {@link BillingPeriod#chargeFor} üretir (yıllıkta ×12).
 *
 * <p>⚠️ Para birimi <b>tek</b>, tüm plan/dönemler için ortaktır. Hücre başına
 * para birimi, aynı sepette iki farklı birim doğurabilirdi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPricingService {

    /** Eski tek anahtar (V33-V36). Yalnız <b>geriye dönük okuma</b> için. */
    public static final String LEGACY_PRICE_KEY = "plan.pro.price";
    public static final String CURRENCY_KEY = "plan.pro.currency";

    /** Sağlayıcıların kabul ettiği ve arayüzün simgesini bildiği para birimleri. */
    private static final Set<String> CURRENCIES = Set.of("USD", "EUR", "GBP", "TRY");

    private static final BigDecimal MAX_PRICE = new BigDecimal("9999.99");

    /** Kayıt yoksa kullanılan varsayılanlar — tanıtım sayfasındaki vaatle aynı. */
    private static final BigDecimal DEFAULT_PRO_MONTHLY = new BigDecimal("13.00");
    private static final BigDecimal DEFAULT_PRO_YEARLY = new BigDecimal("10.00");
    private static final BigDecimal DEFAULT_PREMIUM_MONTHLY = new BigDecimal("30.00");
    private static final BigDecimal DEFAULT_PREMIUM_YEARLY = new BigDecimal("25.00");

    private final AppSettingRepository settingRepository;

    @Value("${waydee.payment.pro-currency:USD}")
    private String fallbackCurrency;

    // --------------------------------------------------------------- okuma

    /** Tek hücre: bu plan + bu dönem için <b>aylık eşdeğer</b> fiyat. */
    @Transactional(readOnly = true)
    public PlanPrice priceOf(UserPlan plan, BillingPeriod period) {
        return new PlanPrice(plan, period, monthlyEquivalent(plan, period), currency());
    }

    /** Ödeme oturumunun kullandığı <b>tahsil edilecek</b> tutar. */
    @Transactional(readOnly = true)
    public BigDecimal chargeFor(UserPlan plan, BillingPeriod period) {
        return normalize(period.chargeFor(monthlyEquivalent(plan, period)));
    }

    /** Arayüzün fiyat tablosunu çizmesi için dört hücre birden. */
    @Transactional(readOnly = true)
    public PricingTable table() {
        String cur = currency();
        return new PricingTable(
                monthlyEquivalent(UserPlan.PRO, BillingPeriod.MONTHLY),
                monthlyEquivalent(UserPlan.PRO, BillingPeriod.YEARLY),
                monthlyEquivalent(UserPlan.PREMIUM, BillingPeriod.MONTHLY),
                monthlyEquivalent(UserPlan.PREMIUM, BillingPeriod.YEARLY),
                cur);
    }

    /** Yönetim görünümü: dört hücre + kaynağı (DATABASE | ENV) + son değişiklik. */
    @Transactional(readOnly = true)
    public PricingAdminView adminView() {
        PricingTable t = table();
        boolean fromDb = settingRepository.findById(key(UserPlan.PRO, BillingPeriod.MONTHLY)).isPresent();
        Instant updatedAt = settingRepository.findById(key(UserPlan.PRO, BillingPeriod.MONTHLY))
                .map(AppSetting::getUpdatedAt)
                .orElse(null);
        return new PricingAdminView(t, fromDb ? "DATABASE" : "ENV", updatedAt);
    }

    // --------------------------------------------------------------- yazma

    /**
     * Fiyat tablosunu günceller.
     *
     * <p>⚠️ Değişiklik <b>geriye dönük değildir</b>: açık duran ödeme oturumları
     * kendi tutarlarıyla tamamlanır (tutar {@code payment_checkouts} satırına
     * yazılmıştır). Aksi halde kullanıcı ödeme sayfasındayken fiyatı değiştirmek
     * onu farklı bir tutarla karşı karşıya bırakırdı.
     *
     * <p>⚠️ Yıllık, aylıktan <b>büyük olamaz</b>: yıllık taahhüt indirim demektir,
     * tersi bir tablo arayüzde "%-23 indirim" gibi negatif rozetler doğururdu.
     */
    @Transactional
    public PricingAdminView update(PricingTable next) {
        String cur = (next.currency() == null ? "" : next.currency().trim().toUpperCase(Locale.ROOT));
        if (!CURRENCIES.contains(cur)) {
            throw ApiException.badRequest("Para birimi şunlardan biri olmalı: " + String.join(", ", CURRENCIES));
        }
        BigDecimal proMonthly = normalize(validate(next.proMonthly(), "Pro aylık"));
        BigDecimal proYearly = normalize(validate(next.proYearly(), "Pro yıllık"));
        BigDecimal premiumMonthly = normalize(validate(next.premiumMonthly(), "Premium aylık"));
        BigDecimal premiumYearly = normalize(validate(next.premiumYearly(), "Premium yıllık"));

        assertDiscount(proYearly, proMonthly, "Pro");
        assertDiscount(premiumYearly, premiumMonthly, "Premium");

        put(key(UserPlan.PRO, BillingPeriod.MONTHLY), proMonthly.toPlainString());
        put(key(UserPlan.PRO, BillingPeriod.YEARLY), proYearly.toPlainString());
        put(key(UserPlan.PREMIUM, BillingPeriod.MONTHLY), premiumMonthly.toPlainString());
        put(key(UserPlan.PREMIUM, BillingPeriod.YEARLY), premiumYearly.toPlainString());
        put(CURRENCY_KEY, cur);

        log.info("Üyelik fiyat tablosu güncellendi: Pro {}/{} · Premium {}/{} {}",
                proMonthly.toPlainString(), proYearly.toPlainString(),
                premiumMonthly.toPlainString(), premiumYearly.toPlainString(), cur);
        return adminView();
    }

    // ---------------------------------------------------------- yardımcılar

    private BigDecimal monthlyEquivalent(UserPlan plan, BillingPeriod period) {
        return settingRepository.findById(key(plan, period))
                .map(AppSetting::getValue)
                .map(PlanPricingService::parsePrice)
                .map(PlanPricingService::normalize)
                .orElseGet(() -> legacyOrDefault(plan, period));
    }

    /**
     * Kayıt yoksa: PRO aylık için <b>eski tek anahtar</b> denenir.
     *
     * <p>⚠️ Bu geriye dönük yol olmadan, V37 öncesi fiyatını elle değiştirmiş
     * bir kurulum yükseltmeden sonra sessizce varsayılana dönerdi.
     */
    private BigDecimal legacyOrDefault(UserPlan plan, BillingPeriod period) {
        if (plan == UserPlan.PRO && period == BillingPeriod.MONTHLY) {
            BigDecimal legacy = settingRepository.findById(LEGACY_PRICE_KEY)
                    .map(AppSetting::getValue)
                    .map(PlanPricingService::parsePrice)
                    .orElse(null);
            if (legacy != null) {
                return normalize(legacy);
            }
        }
        return defaultOf(plan, period);
    }

    private static BigDecimal defaultOf(UserPlan plan, BillingPeriod period) {
        if (plan == UserPlan.PREMIUM) {
            return period.yearly() ? DEFAULT_PREMIUM_YEARLY : DEFAULT_PREMIUM_MONTHLY;
        }
        return period.yearly() ? DEFAULT_PRO_YEARLY : DEFAULT_PRO_MONTHLY;
    }

    private String currency() {
        return settingRepository.findById(CURRENCY_KEY)
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(fallbackCurrency)
                .toUpperCase(Locale.ROOT);
    }

    /** {@code plan.pro.monthly.price} · {@code plan.premium.yearly.price} … */
    private static String key(UserPlan plan, BillingPeriod period) {
        return "plan." + plan.name().toLowerCase(Locale.ROOT)
                + "." + period.name().toLowerCase(Locale.ROOT) + ".price";
    }

    private void put(String key, String value) {
        settingRepository.findById(key).ifPresentOrElse(
                s -> s.update(value),
                () -> settingRepository.save(new AppSetting(key, value)));
    }

    private static BigDecimal validate(BigDecimal price, String label) {
        if (price == null || price.signum() <= 0) {
            throw ApiException.badRequest(label + " ücreti sıfırdan büyük olmalı");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw ApiException.badRequest(label + " ücreti " + MAX_PRICE.toPlainString() + " değerini aşamaz");
        }
        return price;
    }

    private static void assertDiscount(BigDecimal yearly, BigDecimal monthly, String label) {
        if (yearly.compareTo(monthly) > 0) {
            throw ApiException.badRequest(
                    label + " yıllık aylık fiyatı (%s) aylık fiyattan (%s) yüksek olamaz"
                            .formatted(yearly.toPlainString(), monthly.toPlainString()));
        }
    }

    private static BigDecimal normalize(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    /** Bozuk kayıt uygulamayı düşürmesin — okunamayan değer varsayılana düşer. */
    private static BigDecimal parsePrice(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("app_settings fiyatı sayıya çevrilemedi: {} — varsayılan kullanılıyor", raw);
            return null;
        }
    }

    // ------------------------------------------------------------- kayıtlar

    /** Tek hücre. {@code monthlyEquivalent} aylık eşdeğerdir; yıllıkta ×12 tahsil edilir. */
    public record PlanPrice(UserPlan plan, BillingPeriod period,
                            BigDecimal monthlyEquivalent, String currency) {

        /** Tahsil edilecek tutar. */
        public BigDecimal charge() {
            return period.chargeFor(monthlyEquivalent).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** Dört hücre + ortak para birimi. Değerler <b>aylık eşdeğerdir</b>. */
    public record PricingTable(BigDecimal proMonthly, BigDecimal proYearly,
                               BigDecimal premiumMonthly, BigDecimal premiumYearly,
                               String currency) {

        /** Yıllık taahhüdün yüzde indirimi — arayüzdeki rozet bunu yazar. */
        public List<Integer> yearlyDiscountPercents() {
            return List.of(discount(proMonthly, proYearly), discount(premiumMonthly, premiumYearly));
        }

        private static int discount(BigDecimal monthly, BigDecimal yearly) {
            if (monthly == null || yearly == null || monthly.signum() <= 0) {
                return 0;
            }
            return monthly.subtract(yearly)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(monthly, 0, RoundingMode.HALF_UP)
                    .intValue();
        }
    }

    /** Yönetim görünümü — {@code source}: DATABASE | ENV. */
    public record PricingAdminView(PricingTable prices, String source, Instant updatedAt) {
    }
}
