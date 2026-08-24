package com.waydee.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Barındırılan ödeme sayfası soyutlaması (port).
 *
 * <p>Eski {@code PaymentGateway} <b>senkron</b> bir tahsilattı (sahte geçit
 * anında "başarılı" derdi). Gerçek sağlayıcılarda akış üç adımlıdır:
 * <ol>
 *   <li>sunucu bir <b>ödeme oturumu</b> açar ve URL alır,</li>
 *   <li>kullanıcı o sayfada öder,</li>
 *   <li>sonuç <b>webhook</b> ile geri gelir.</li>
 * </ol>
 * Bu arayüz yalnız 1. adımı kapsar; 2 ve 3 sağlayıcının işidir.
 *
 * <p><b>Neden port:</b> sağlayıcı değişimi bu arayüzün yeni bir uygulaması
 * olmalı, iş mantığına dokunmamalı — projedeki
 * `StorageService`/`DomainEventPublisher` deseninin aynısı. Portun değerini
 * 12 Ağu 2026'da ölçtük: LemonSqueezy tamamen çıkarılıp yerine Polar konurken
 * {@code CheckoutService}'te <b>tek satır</b> bile değişmedi.
 */
public interface PaymentProvider {

    /** Yapılandırmadaki sağlayıcı adı (DB'ye ve faturaya yazılır). */
    String name();

    /**
     * Ödeme oturumu açar.
     *
     * @param request tutar, para birimi ve dönüş için taşınacak referans
     * @return kullanıcının yönlendirileceği adres + sağlayıcıdaki oturum kimliği
     */
    HostedCheckout createCheckout(CheckoutRequest request);

    /**
     * @param reference bizim rezervasyon kimliğimiz — sağlayıcı bunu webhook'ta
     *                  geri döndürür ve ödeme doğru rezervasyonla eşleşir
     * @param amount    tahsil edilecek tutar (para biriminin ana birimi, ör. ₺)
     * @param email     alıcının e-postası (ödeme sayfası önden doldurulur)
     * @param plan      satın alınan üyelik planı — <b>Polar ürün seçimi</b> buna bakar
     * @param period    faturalama dönemi — plan ile birlikte ürünü belirler
     *
     * <p>⚠️ {@code plan}/{@code period} eklendi (12 Ağu 2026): Polar'da tutar
     * istekten gönderilmez, <b>ürün kimliğinden</b> gelir. Sağlayıcının doğru
     * ürünü seçebilmesi için ne satıldığını bilmesi gerekir. {@code amount}
     * yine de taşınır — sahte sağlayıcı ve loglar onu kullanır.
     */
    record CheckoutRequest(
            UUID reference,
            BigDecimal amount,
            String currency,
            String productName,
            String description,
            String email,
            String successUrl,
            com.waydee.identity.domain.UserPlan plan,
            com.waydee.identity.domain.BillingPeriod period
    ) {
    }

    record HostedCheckout(String url, String providerCheckoutId) {
    }
}
