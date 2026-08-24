package com.waydee.common.storage;

import java.io.InputStream;

/**
 * Nesne depolama soyutlaması. MinIO implementasyonu vardır;
 * S3 ya da başka sağlayıcıya geçiş yeni implementasyonla yapılır.
 */
public interface StorageService {

    void put(String objectKey, InputStream stream, long size, String contentType);

    InputStream get(String objectKey);

    /**
     * Nesneye doğrudan erişim veren, kısa ömürlü imzalı adres.
     *
     * <p>Backend'in baytları kendi üzerinden akıtmaması için vardır: indirme
     * süresince bir Tomcat thread'i bloke oluyor, bant genişliği iki kez
     * ödeniyor ve yanıt CDN'e alınamıyordu (ölçek analizi bulgusu K1).
     *
     * <p>Sağlayıcı imzalı adres üretemezse {@code null} döner — çağıran taraf
     * bu durumda eski akıtma yoluna düşer.
     */
    String presignedUrl(String objectKey, java.time.Duration ttl);

    void delete(String objectKey);
}
