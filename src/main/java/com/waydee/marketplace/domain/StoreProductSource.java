package com.waydee.marketplace.domain;

/**
 * Raf ürününün nereden geldiği.
 *
 * <p>Kullanıcı isteği ikisini de açıkça istedi: <i>"hem profildeki ürünler
 * görünsün seçip hemen ekleyebilsin rafa, hem de yeni ürünleri sadece o
 * metaverse sürecinde ekleyebileyim."</i>
 */
public enum StoreProductSource {

    /**
     * Kullanıcının mevcut gönderisi. Görsel <b>gönderiden</b> gelir; burada
     * ikinci bir kopya tutulmaz — gönderi fotoğrafı değişirse raf da değişmeli.
     */
    POST("Profilden"),

    /**
     * Yalnız mağazaya özel ürün; kullanıcının profilinde/akışında görünmez.
     * Görselini kendisi yükler.
     */
    CUSTOM("Mağazaya özel");

    private final String label;

    StoreProductSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
