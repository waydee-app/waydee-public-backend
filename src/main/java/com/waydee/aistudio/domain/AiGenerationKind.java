package com.waydee.aistudio.domain;

/**
 * Stüdyo aracının türü (V45).
 *
 * <p>Bugün tek bir tür üretimde: {@link #FASHION_MODEL}. Arayüzdeki diğer
 * sekmeler (<i>EDITORIAL/SWAP · POSE · VIDEO · ARAÇLAR</i>) <b>"yakında"</b>
 * rozetiyle duruyor ve tıklanamıyor.
 *
 * <p>⚠️ Tür kolonu şimdiden var: ikinci araç geldiğinde yeni bir tablo ve yeni
 * bir galeri sorgusu gerekmesin diye. Kolonu sonradan eklemek, mevcut satırlara
 * geriye dönük bir değer uydurmayı gerektirirdi.
 */
public enum AiGenerationKind {

    /** Ürün görsellerini yapay zekâ mankeni üzerinde giydirir ("Fast Model"). */
    FASHION_MODEL
}
