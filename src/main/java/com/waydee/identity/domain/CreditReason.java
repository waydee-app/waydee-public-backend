package com.waydee.identity.domain;

/**
 * Kredi hareketinin sebebi (V45) — defterdeki her satır bunlardan biridir.
 *
 * <p>⚠️ Serbest metin yerine sabit küme: rapor ve destek "bu kredi nereden
 * geldi" sorusunu <b>saymak</b> zorunda ve serbest metin sayılamaz.
 */
public enum CreditReason {

    /** Üyelik dönemi başladı/yenilendi → paketin kredisi yüklendi. */
    GRANT_PLAN,

    /**
     * <b>Ücretsiz hoş geldin kredisi</b> (18 Ağu 2026) — hesap ömründe bir kez,
     * stüdyoyu tam kapasitede bir kez denemeye yeter kadar.
     *
     * <p>⚠️ {@link #GRANT_PLAN}'dan ayrı bir sebep: ikisi de "kredi geldi" ama
     * biri <b>ödemenin karşılığı</b>, diğeri <b>pazarlama gideri</b>. Aynı
     * kovaya atarsak "ne kadar bedava kredi dağıttık" sorusu sayılamaz hâle
     * gelir — bu enum'un var oluş sebebi tam olarak buydu.
     */
    GRANT_WELCOME,

    /** Görsel üretimi için düşüldü. */
    SPEND,

    /** Üretim başarısız oldu → düşülen kredi geri verildi. */
    REFUND,

    /** Yönetici düzeltmesi (destek, telafi). */
    ADMIN_ADJUST
}
