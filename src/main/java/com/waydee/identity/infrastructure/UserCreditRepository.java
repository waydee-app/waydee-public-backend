package com.waydee.identity.infrastructure;

import com.waydee.identity.domain.UserCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserCreditRepository extends JpaRepository<UserCredit, UUID> {

    /**
     * <b>Atomik harcama.</b> Yeter bakiye varsa düşer ve {@code 1}, yoksa hiçbir
     * şey yapmaz ve {@code 0} döner.
     *
     * <p>🔴 <b>Bu sorgu, kredi ekonomisinin tek güvenlik kapısıdır.</b> "Önce
     * oku, yeterliyse yaz" iki adımı kullanılsaydı iki eşzamanlı istek aynı
     * bakiyeyi okur ve ikisi de harcardı — kullanıcı 60 kredilik bakiyeyle iki
     * görsel üretirdi. Koşul {@code WHERE} içindedir, yani karar veritabanının
     * satır kilidiyle birlikte verilir.
     *
     * <p>⚠️ {@code clearAutomatically} <b>bilerek kullanılmadı</b>: entity'yi
     * detach eder ve sonraki yazımlar sessizce kaybolur (86. turun logo hatası).
     * Çağıran zaten bakiyeyi bu çağrıdan <b>sonra</b> ayrıca okuyor.
     */
    @Modifying
    @Query("""
            update UserCredit c
               set c.balance = c.balance - :cost,
                   c.spentTotal = c.spentTotal + :cost,
                   c.updatedAt = CURRENT_TIMESTAMP
             where c.userId = :userId
               and c.balance >= :cost
            """)
    int tryDebit(@Param("userId") UUID userId, @Param("cost") int cost);
}
