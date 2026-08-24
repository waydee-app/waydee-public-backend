package com.waydee.payment.application;

/**
 * <b>MockPaymentProvider</b> — bu sinifin govdesi <b>VITRIN KOPYASINDA KALDIRILMISTIR</b>.
 *
 * <p>Ozgun sorumlulugu: Yerel gelistirme icin sahte odeme gecidi.
 *
 * <p>Bu depo Waydee'nin <b>yalnizca inceleme amacli</b> bir kopyasidir.
 * Kimlik dogrulama, imzalama, odeme ve sir yonetimiyle ilgili siniflarin
 * icerigi bilerek cikarilmistir; depo <b>derlenemez ve calistirilamaz</b>
 * (yapi dosyalari da kaldirilmistir).
 *
 * <p>Kaldirilan bolum yaklasik <b>44 satirdi</b>.
 */
public final class MockPaymentProvider {

    private MockPaymentProvider() {
        throw new UnsupportedOperationException(
                "Vitrin kopyasi: bu sinifin uygulamasi yayinlanmamistir.");
    }
}
