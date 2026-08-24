package com.waydee.social.domain;

/** Bölge profilinin gösterim türü. */
public enum ProfileType {
    /** Klasik sosyal profil: gönderi akışı. */
    STANDARD,
    /** Sahibin eklediği web sitesi — kart olarak görünür, tıklayınca gömülü açılır. */
    WEBSITE,
    /** Sahibin yazdığı HTML — mini web sitesi gibi gösterilir (sandbox iframe). */
    HTML
}
