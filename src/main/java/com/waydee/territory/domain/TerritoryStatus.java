package com.waydee.territory.domain;

public enum TerritoryStatus {
    /** Kirası süren, haritada görünen bölge. */
    ACTIVE,
    /** Admin tarafından pasife alındı — geri getirmesi de admindedir. */
    REVOKED,
    /**
     * Kiralama süresi doldu. REVOKED'dan bilinçli olarak ayrıdır: buradan
     * çıkış yolu <b>sahibinin yenilemesidir</b>, admin müdahalesi değil.
     */
    EXPIRED
}
