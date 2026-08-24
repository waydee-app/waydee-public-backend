package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.identity.domain.BillingPeriod;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserPlan;
import com.waydee.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Plan <b>değiştirme</b> — okuma {@link PlanService}'te, yazma burada.
 *
 * <p>⚠️ Ayrı servis bilinçli: plan okumak her istekte olur (ucuz, salt okunur),
 * plan değiştirmek nadir ve <b>yazan</b> bir işlemdir. İkisini tek serviste
 * toplamak, salt okunur transaction'ları gereksiz yere yazma transaction'ına
 * çevirirdi.
 */
@Service
@RequiredArgsConstructor
public class PlanUpgradeService {

    private final UserRepository userRepository;
    private final CreditService creditService;

    /**
     * Ödeme tamamlandıktan sonra çağrılır (webhook yolu).
     *
     * <p>🔴 Üyelik <b>sürelidir</b> (V35, V37'de dönem eklendi): süresiz plan
     * vermez, ödenen dönem kadar (30 ya da 365 gün) uzatır. Aynı plan
     * yenileniyorsa kalan gün kaybolmasın diye mevcut bitişin üstüne biner.
     */
    @Transactional
    public void grant(UUID userId, UserPlan plan, BillingPeriod period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        user.grantPlan(plan, period);
        userRepository.save(user);
        grantCredits(user, plan, period);
    }

    /**
     * Yönetim: plan verme/geri alma (destek, deneme, iade).
     *
     * <p>Ücretli plan verildiğinde de <b>süreli</b> verilir — elle verilen
     * üyelik ödenmiş üyelikten farklı davranmamalı.
     */
    @Transactional
    public UserPlan change(UUID userId, UserPlan plan, BillingPeriod period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        BillingPeriod effective = period == null ? BillingPeriod.MONTHLY : period;
        user.grantPlan(plan, effective);
        userRepository.save(user);
        grantCredits(user, plan, effective);
        return plan;
    }

    /**
     * <b>Üyelikle birlikte yapay zekâ kredisi yükle</b> (V45).
     *
     * <p>🔴 Burada, {@code grantPlan}'ın hemen ardında çağrılır — üyeliği
     * uzatan <b>her</b> yol (ödeme webhook'u · kupon · yönetici) buradan geçer.
     * Yüklemeyi yalnız webhook'a koymak, kuponla Premium olan kullanıcıyı
     * kredisiz bırakırdı.
     *
     * <p>⚠️ Tekrar koruması {@link CreditService#grantForPlan} içindedir ve
     * anahtarı <b>bitiş anıdır</b>: aynı dönemin ikinci bildirimi ikinci bir
     * yükleme yapamaz. Bu yüzden burada ek bir kontrol yoktur.
     *
     * <p>⚠️ FREE'ye düşüşte hiçbir şey yapılmaz — bakiye durur ama üretim kapısı
     * ({@code CreditService.assertCanUseAi}) ücretli üyelik ister, yani biriken
     * kredi üyeliksiz kullanılamaz.
     */
    private void grantCredits(User user, UserPlan plan, BillingPeriod period) {
        if (!plan.paid()) {
            return;
        }
        creditService.grantForPlan(user.getId(), plan, period, user.getPlanExpiresAt());
    }
}
