package com.waydee.social.domain;

/**
 * Bildirim turleri.
 *
 * <p>Iliski olaylari: takip, takip istegi, istek kabul, profil goruntuleme.
 * Gonderi olaylari (V39): begeni, kaydetme.
 *
 * <p>UYARI: yeni tur eklerken `notifications.type` CHECK kisiti da
 * genisletilmeli - enum tek basina yetmez, veritabani satiri reddeder.
 */
public enum NotificationType {
    FOLLOW,
    FOLLOW_REQUEST,
    FOLLOW_ACCEPTED,
    PROFILE_VIEW,

    /** Biri gonderini begendi (V39). */
    POST_LIKE,

    /**
     * Biri gonderini kaydetti (V39).
     *
     * <p>V38'de kaydetme bilincli olarak SESSIZDI. Kullanici tersini istedi:
     * bu urunde kaydetme, begeniden guclu bir sinyal (satin alma niyeti) ve
     * sahibinin gormesi isteniyor. Gerekce burada kalsin ki bir dahaki turda
     * "sessiz olmaliydi" diye geri alinmasin.
     */
    POST_SAVE
}
