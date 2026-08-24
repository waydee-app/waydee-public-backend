package com.waydee.common.mail;

/**
 * Gönderilmeye hazır bir e-posta.
 *
 * <p>Düz metin karşılığı <b>zorunludur</b>: yalnız HTML gönderen iletiler spam
 * filtrelerinde ceza alır ve metin tabanlı istemcilerde okunamaz.
 *
 * @param subject konu
 * @param html    HTML gövde
 * @param text    düz metin gövde (aynı içeriğin sade hali)
 */
public record EmailMessage(String subject, String html, String text) {
}
