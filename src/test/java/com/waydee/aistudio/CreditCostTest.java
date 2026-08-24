package com.waydee.aistudio;

import com.waydee.aistudio.application.CreditCost;
import com.waydee.identity.domain.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <b>Kredi maliyeti bekçisi.</b>
 *
 * <p>🔴 Asıl iş {@link #hosGeldinKredisiEnPahaliUretimeYETER()} testinde:
 * ücretsiz hoş geldin kredisi ({@link UserPlan#FREE_WELCOME_CREDITS}) <b>elle
 * yazılmış bir sayıdır</b> çünkü {@code identity} modülü {@code aistudio}'ya
 * bağımlı olamaz (bağımlılık ters yönde; sabiti oradan almak döngü olurdu).
 * Elle yazılmış her sayı sessizce eskir: {@code CreditCost} formülü değişirse
 * ücretsiz kullanıcı stüdyoyu "bir kez tam kapasitede deneyemez" hâle gelir ve
 * bunu kimse fark etmez. Bu test o sessizliği bozar.
 */
class CreditCostTest {

    @Test
    @DisplayName("Hoş geldin kredisi, en pahalı TEK üretimin maliyetine eşittir")
    void hosGeldinKredisiEnPahaliUretimeYETER() {
        int enPahali = CreditCost.of(true, CreditCost.MAX_PRODUCTS);

        assertEquals(enPahali, UserPlan.FREE_WELCOME_CREDITS,
                "Ücretsiz deneme, stüdyonun en pahalı tek üretimini TAM olarak "
                        + "karşılamalı (kullanıcı talimatı: \"bir defa ai toolu max "
                        + "kullanacak kadar kredi\"). CreditCost formülü değiştiyse "
                        + "UserPlan.FREE_WELCOME_CREDITS de güncellenmeli.");
    }

    @Test
    @DisplayName("Maliyet: temel(kalite) + (ürün-1) × ek")
    void maliyetFormulu() {
        assertEquals(CreditCost.BASE_STANDARD, CreditCost.of(false, 1));
        assertEquals(CreditCost.BASE_HIGH, CreditCost.of(true, 1));
        assertEquals(CreditCost.BASE_STANDARD + 2 * CreditCost.EXTRA_PRODUCT,
                CreditCost.of(false, 3));
    }

    @Test
    @DisplayName("Ürün sayısı tavana ve tabana KIRPILIR — istemciden gelen sayı güvenilmezdir")
    void urunSayisiKirpilir() {
        assertEquals(CreditCost.of(true, CreditCost.MAX_PRODUCTS), CreditCost.of(true, 99));
        assertEquals(CreditCost.of(true, 1), CreditCost.of(true, 0));
        assertEquals(CreditCost.of(true, 1), CreditCost.of(true, -5));
    }

}
