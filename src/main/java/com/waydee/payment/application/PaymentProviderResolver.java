package com.waydee.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Yapılandırmadaki sağlayıcıyı seçer.
 *
 * <p>Seçim çalışma zamanında yapılır (bean koşulu ile değil): iki uygulamayı da
 * yüklü tutmak, yerelde sahte akışla gerçek akış arasında geçişi yeniden
 * başlatmadan test edilebilir kılar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties({PaymentProperties.class, PolarProperties.class})
public class PaymentProviderResolver {

    private final PaymentProperties properties;
    private final MockPaymentProvider mock;
    private final PolarProvider polar;

    /**
     * <b>GERÇEK TAHSİLAT AÇIK</b> (12 Ağu 2026, kullanıcı talimatı: *"ödeme alt
     * yapısını Polar'a geçireceğim… LemonSqueezy'yi tamamen kaldır"*).
     *
     * <p>🔴 9 Ağu'dan 12 Ağu'ya kadar bu metot <b>koşulsuz {@code mock}</b>
     * döndürüyordu; üyelikler uygulama içindeki sahte onay ekranından
     * yükseliyordu ve hiç para tahsil edilmiyordu. O sabit kaldırıldı.
     *
     * <p>Seçim yapılandırmadan gelir: {@code PAYMENT_PROVIDER=polar} ise Polar,
     * aksi halde sahte sağlayıcı. Üretimde {@code mock} seçilmesi
     * {@code ProductionSecretsGuard} tarafından engellenir — sahte geçit bedava
     * üyelik dağıtmak demektir.
     */
    public PaymentProvider active() {
        return properties.isPolar() ? polar : mock;
    }

    /** Sahte onay ucu ({@code /checkouts/{id}/mock-confirm}) bu bayrakla açılır. */
    public boolean isMock() {
        return !properties.isPolar();
    }
}
