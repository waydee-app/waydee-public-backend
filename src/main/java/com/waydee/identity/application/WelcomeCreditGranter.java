package com.waydee.identity.application;

import com.waydee.identity.domain.CreditLedgerEntry;
import com.waydee.identity.domain.CreditReason;
import com.waydee.identity.domain.UserCredit;
import com.waydee.identity.domain.UserPlan;
import com.waydee.identity.infrastructure.CreditLedgerRepository;
import com.waydee.identity.infrastructure.UserCreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * <b>Ücretsiz hoş geldin kredisi</b> — hesap ömründe <b>bir kez</b>
 * ({@link UserPlan#FREE_WELCOME_CREDITS}, 18 Ağu 2026).
 *
 * <h3>🔴 Neden ayrı bir sınıf</h3>
 * <p>Yükleme kendi transaction'ında ({@code REQUIRES_NEW}) koşmalıdır: iki
 * eşzamanlı istek aynı anda yüklemeye kalkarsa tekil kısıt patlar ve bu hata,
 * çağıranın transaction'ını <b>rollback-only</b> bırakırdı — yani bakiyesini
 * okumak isteyen kullanıcı 500 görürdü. Metot {@code CreditService} içinde
 * kalsaydı <b>kendi kendine çağrı</b> olur, Spring proxy'si devreye girmez ve
 * {@code REQUIRES_NEW} hiç uygulanmazdı. Bu, projedeki
 * {@code CreditService.grantForPlan} ile <b>aynı</b> gerekçedir; oradaki çağrı
 * dışarıdan (ödeme akışından) geldiği için ayrı sınıfa gerek kalmamıştı.
 *
 * <h3>Tekillik</h3>
 * <p>{@code ref_key = welcome:<userId>} ve kolon UNIQUE. Bayrak kolonu
 * <b>açılmadı</b>: defter zaten "bu hesap hoş geldin kredisi aldı mı" sorusunun
 * tek gerçeğidir ve ikinci bir gerçek, ikisinin sapması demektir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeCreditGranter {

    private final UserCreditRepository creditRepository;
    private final CreditLedgerRepository ledgerRepository;

    /**
     * Hakkı henüz verilmemişse verir; verilmişse <b>sessizce döner</b>.
     *
     * <p>⚠️ {@code add} kullanılır, {@link UserCredit#grantPackage} değil.
     * İkincisi bakiyeyi pakete <b>eşitler</b>; hoş geldin kredisi ise mevcut
     * bakiyenin <b>üstüne</b> binen bir hediyedir ve Premium bir hesabın
     * 10.000'ini 150'ye düşürmesi kabul edilemez.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureFor(UUID userId) {
        String refKey = refKey(userId);
        if (ledgerRepository.existsByRefKey(refKey)) {
            return;
        }
        try {
            UserCredit credit = creditRepository.findById(userId)
                    .orElseGet(() -> creditRepository.save(new UserCredit(userId)));
            credit.add(UserPlan.FREE_WELCOME_CREDITS);
            creditRepository.save(credit);
            ledgerRepository.save(new CreditLedgerEntry(userId, UserPlan.FREE_WELCOME_CREDITS,
                    credit.getBalance(), CreditReason.GRANT_WELCOME,
                    "Hoş geldin denemesi", refKey));
            log.info("Hoş geldin kredisi verildi: {} → {}", userId, UserPlan.FREE_WELCOME_CREDITS);
        } catch (DataIntegrityViolationException e) {
            // Yarış: aynı anahtarı başka bir thread yazdı. Hata değil, kısıtın
            // var oluş sebebi. (grantForPlan'daki davranışın aynısı.)
            log.info("Hoş geldin kredisi zaten verilmiş (ref={})", refKey);
        }
    }

    static String refKey(UUID userId) {
        return "welcome:" + userId;
    }
}
