package com.waydee.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
@EnableConfigurationProperties(StorageProperties.class)
public class MinioStorageService implements StorageService {

    private final StorageProperties properties;
    private final MinioClient client;

    public MinioStorageService(StorageProperties properties) {
        this.properties = properties;
        // ⚠️ MinIO istemcisi S3 sözleşmesini konuşur, bu yüzden endpoint AWS S3'e
        // çevrilerek gerçek S3 ile de kullanılabilir. Tek koşul: AWS imzası bölge
        // adını içerir → `region` boşsa istek SignatureDoesNotMatch ile döner.
        // Yerel MinIO bölge bilmez; bu yüzden değer opsiyonel bırakıldı.
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey());
        if (properties.region() != null && !properties.region().isBlank()) {
            builder.region(properties.region());
        }
        this.client = builder.build();
    }

    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                log.info("MinIO bucket oluşturuldu: {}", properties.bucket());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO bucket hazırlanamadı: " + properties.bucket(), ex);
        }
    }

    @Override
    public void put(String objectKey, InputStream stream, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Nesne depolamaya yazılamadı: " + objectKey, ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Nesne depolamadan okunamadı: " + objectKey, ex);
        }
    }

    /**
     * S3/MinIO'nun kendi imzalı GET adresi (presigned URL).
     *
     * <p>🔴 Üst sınır <b>7 gün</b>: AWS SigV4 imzasında {@code X-Amz-Expires}
     * 604800 saniyeyi aşamaz, aşarsa S3 isteği reddeder.
     *
     * <p>Hata halinde fırlatmaz, {@code null} döner — medya teslimi imzalı
     * adres üretilemedi diye tamamen kesilmemeli, akıtmaya geri düşmeli.
     */
    @Override
    public String presignedUrl(String objectKey, Duration ttl) {
        int seconds = (int) Math.min(Math.max(ttl.getSeconds(), 1), 7 * 24 * 3600L);
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(seconds)
                    .build());
        } catch (Exception ex) {
            log.warn("İmzalı medya adresi üretilemedi ({}), akıtmaya düşülüyor: {}", objectKey, ex.toString());
            return null;
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Nesne depolamadan silinemedi: " + objectKey, ex);
        }
    }
}
