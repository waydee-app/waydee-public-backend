package com.waydee.common.config;

/**
 * <b>ProductionSecretsGuard</b> — bu sinifin govdesi <b>VITRIN KOPYASINDA KALDIRILMISTIR</b>.
 *
 * <p>Ozgun sorumlulugu: Uretimde gelistirme sirlariyla acilisi ENGELLEYEN kapi (kisa JWT anahtari, dev sifreleri, acik Swagger, sahte odeme gecidi).
 *
 * <p>Bu depo Waydee'nin <b>yalnizca inceleme amacli</b> bir kopyasidir.
 * Kimlik dogrulama, imzalama, odeme ve sir yonetimiyle ilgili siniflarin
 * icerigi bilerek cikarilmistir; depo <b>derlenemez ve calistirilamaz</b>
 * (yapi dosyalari da kaldirilmistir).
 *
 * <p>Kaldirilan bolum yaklasik <b>261 satirdi</b>.
 */
public final class ProductionSecretsGuard {

    private ProductionSecretsGuard() {
        throw new UnsupportedOperationException(
                "Vitrin kopyasi: bu sinifin uygulamasi yayinlanmamistir.");
    }
}
