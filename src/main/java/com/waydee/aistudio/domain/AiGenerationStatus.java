package com.waydee.aistudio.domain;

/**
 * Üretimin yaşam döngüsü (V45).
 *
 * <p>⚠️ {@code QUEUED} ile {@code RUNNING} <b>ayrı</b>: ilki "kredi düşüldü,
 * kayıt açıldı ama dış servise henüz gidilmedi", ikincisi "sağlayıcıda". Ayrım
 * teşhis için gereklidir — {@code QUEUED}'da takılan bir satır bizim
 * kuyruğumuzun, {@code RUNNING}'de takılan satır <b>sağlayıcının</b> sorunudur.
 */
public enum AiGenerationStatus {

    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    /** İş hâlâ sürüyor mu — eşzamanlı üretim tavanı bunu sayar. */
    public boolean active() {
        return this == QUEUED || this == RUNNING;
    }
}
