package com.waydee.billing.domain;

/** Kuponun hangi işlemde geçerli olduğu. */
public enum CouponScope {
    /** Yalnız yeni bölge kiralama. */
    PURCHASE,
    /** Yalnız kira yenileme. */
    RENEWAL,
    /** İkisinde de. */
    BOTH
}
