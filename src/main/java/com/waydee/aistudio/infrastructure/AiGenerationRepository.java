package com.waydee.aistudio.infrastructure;

import com.waydee.aistudio.domain.AiGeneration;
import com.waydee.aistudio.domain.AiGenerationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {

    /** Galeri — yeniden eskiye, sayfalı. */
    List<AiGeneration> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Açık iş sayısı — <b>eşzamanlı üretim tavanı</b> bunu sayar.
     *
     * <p>🔴 Bu kapı olmadan bir kullanıcı tek tıkla onlarca istek açıp hem
     * sağlayıcı kotamızı hem de kendi kredisini saniyeler içinde tüketebilir,
     * bu arada uygulama thread'lerini de meşgul ederdi.
     */
    long countByUserIdAndStatusIn(UUID userId, Collection<AiGenerationStatus> statuses);

    /** Saatlik hız sınırı — kredisi bol bir hesabın sağlayıcıyı dövmesini keser. */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /**
     * <b>Takılı kalmış üretimler</b> — süpürme bunları iade eder.
     *
     * <p>🔴 Bu sorgu bir <b>kurtarma</b> yoludur ve olmazsa olmazdır: koşucunun
     * {@code catch} bloğu yalnız <b>başlayan</b> işleri kurtarır. Havuz kuyruğu
     * dolduğunda reddedilen, ya da sunucu yeniden başlatıldığında bellekteki
     * kuyrukla birlikte kaybolan işler hiç başlamaz — kredileri de öylece
     * yanardı.
     */
    List<AiGeneration> findByStatusInAndCreatedAtBefore(Collection<AiGenerationStatus> statuses, Instant before);
}
