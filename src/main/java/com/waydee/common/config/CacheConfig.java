package com.waydee.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis önbelleği — ölçek analizi bulguları K2 (harita uçları her istekte tüm
 * dünyayı çekiyor) ve C7 (projede 0 adet {@code @Cacheable}, Redis kurulu ama
 * yalnız hız sınırı için kullanılıyor).
 *
 * <p>Harita ürünün ana ekranıdır, yani en sık çağrılan uçtur; her çağrıda tüm
 * bölgeler DB'den çekilip GeoJSON Java'da sıfırdan inşa ediliyordu.
 *
 * <p><b>Neden 30 saniye?</b> Harita değişiklikleri istemciye zaten WebSocket ile
 * <i>anında</i> gider ({@code /topic/territories}, {@code /topic/regions});
 * REST ucu yalnız ilk yükleme ve tazelemedir. Üstelik önbellek, değişiklik
 * olaylarında {@code com.waydee.realtime.MapCacheEvictor} tarafından ayrıca
 * boşaltılır — yani 30 saniye bir <b>tavan</b>dır, tipik bayatlık sıfırdır.
 *
 * <p>🔴 <b>Önbellek FAIL-OPEN.</b> Redis düşerse istek hata vermez, doğrudan
 * veritabanına gider. Bu, hız sınırı katmanıyla aynı duruştur: Redis bu projede
 * <b>hızlandırıcıdır, veri kaynağı değil</b>.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /** Harita GeoJSON önbellekleri — hepsi kısa ömürlü. */
    public static final String MAP_REGIONS = "map:regions";
    public static final String MAP_TERRITORIES = "map:territories";
    public static final String MAP_TERRITORIES_PUBLIC = "map:territories:public";

    private static final Duration MAP_TTL = Duration.ofSeconds(30);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        // ⚠️ Değer serileştiricisi JSON: GeoJSON çıktısı zaten düz Map/List
        // yapısıdır (JTS geometrileri GeoJson yardımcısında dönüştürülür), yani
        // Java serileştirmesine gerek yoktur ve önbellek Redis'ten okunabilir kalır.
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("waydee:cache:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper.copy())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        MAP_REGIONS, base.entryTtl(MAP_TTL),
                        MAP_TERRITORIES, base.entryTtl(MAP_TTL),
                        MAP_TERRITORIES_PUBLIC, base.entryTtl(MAP_TTL)))
                .build();
    }

    /**
     * 🔴 Redis erişilemezse istek DÜŞMEZ.
     *
     * <p>Varsayılan davranış istisnayı çağırana kadar taşır; o durumda
     * ElastiCache'in kısa bir kesintisi tüm haritayı 500'e çevirirdi. Burada
     * hata yutulur ve çağrı önbelleksiz devam eder.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Önbellek okunamadı ({}), veritabanına düşülüyor: {}", cache.getName(), ex.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Önbelleğe yazılamadı ({}): {}", cache.getName(), ex.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Önbellek boşaltılamadı ({}): {}", cache.getName(), ex.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Önbellek temizlenemedi ({}): {}", cache.getName(), ex.toString());
            }
        };
    }
}
