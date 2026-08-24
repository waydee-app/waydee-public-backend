package com.waydee.payment.domain;

/** Ödemenin neyin karşılığı olduğu. */
public enum CheckoutKind {
    /** Yeni daire kiralama (12 aylık ilk dönem). */
    TERRITORY_PURCHASE,
    /** Mevcut bölgenin kirasını uzatma. */
    TERRITORY_RENEWAL,

    /**
     * <b>PRO üyeliğe geçiş</b> (V34).
     *
     * <p>⚠️ Daire alımından farkı: <b>geometrisi yoktur</b>. Plan yükseltmesi de
     * bir ödemedir; ayrı bir mekanizma icat etmek yerine bu durum makinesi
     * (PENDING → PAID/FAILED, imzalı webhook, idempotent tamamlama) yeniden
     * kullanılır.
     */
    PLAN_PRO,

    /** <b>PREMIUM üyeliğe geçiş</b> (V37) — mağaza dairesi hakkını açar. */
    PLAN_PREMIUM;

    /** Üyelik ödemesi mi (geometrisiz)? */
    public boolean isPlan() {
        return this == PLAN_PRO || this == PLAN_PREMIUM;
    }

    /** Bu ödemenin karşılık geldiği plan; bölge ödemelerinde {@code null}. */
    public com.waydee.identity.domain.UserPlan plan() {
        return switch (this) {
            case PLAN_PRO -> com.waydee.identity.domain.UserPlan.PRO;
            case PLAN_PREMIUM -> com.waydee.identity.domain.UserPlan.PREMIUM;
            default -> null;
        };
    }

    /** Plandan ödeme türüne — oturum açarken kullanılır. */
    public static CheckoutKind of(com.waydee.identity.domain.UserPlan plan) {
        return plan == com.waydee.identity.domain.UserPlan.PREMIUM ? PLAN_PREMIUM : PLAN_PRO;
    }
}
