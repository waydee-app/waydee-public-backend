package com.waydee.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waydee.storage")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        // AWS S3 kullanılırken zorunlu (imza bölgeye bağlıdır); yerel MinIO'da boş.
        String region,

        /**
         * Medya doğrudan depodan mı teslim edilsin? (ölçek analizi bulgusu K1)
         *
         * <p>{@code true} (varsayılan): backend imzalı depo adresine <b>302</b>
         * döner, baytlar S3'ten doğrudan tarayıcıya akar.
         * {@code false}: eski davranış — baytlar backend'in içinden geçer.
         *
         * <p>Kapatma sebebi olabilecek tek durum: depo adresinin tarayıcıdan
         * erişilemediği kurulumlar (ör. MinIO yalnız iç ağda).
         */
        Boolean directDelivery
) {

    public boolean directDeliveryEnabled() {
        return directDelivery == null || directDelivery;
    }
}
