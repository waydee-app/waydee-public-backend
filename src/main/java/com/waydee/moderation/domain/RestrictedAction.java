package com.waydee.moderation.domain;

/**
 * Kullanıcı bazlı engellenebilen eylemler. Admin tek tek açıp kapatabilir;
 * her biri backend'de ilgili serviste kontrol edilir (istemciye güvenilmez).
 */
public enum RestrictedAction {
    /** Mesaj gönderme (yeni sohbet açma dahil). */
    MESSAGE("Mesaj gönderme"),
    /** Alan satın alma. */
    PURCHASE("Alan satın alma"),
    /** Gönderi paylaşma. */
    POST("Gönderi paylaşma"),
    /** Yorum yazma. */
    COMMENT("Yorum yazma"),
    /** Hikaye paylaşma. */
    STORY("Hikaye paylaşma"),
    /** Takip etme / takip isteği gönderme. */
    FOLLOW("Takip etme"),
    /** Görsel yükleme (avatar dahil tüm medya). */
    UPLOAD("Görsel yükleme"),
    /** Beğeni ve anket/etkinlik etkileşimleri. */
    REACT("Beğeni ve oylama"),
    /** Profil bilgilerini değiştirme. */
    PROFILE_EDIT("Profil düzenleme");

    private final String label;

    RestrictedAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
