package com.waydee.moderation.domain;

/** Şikayet sebepleri (istemcideki seçenek listesiyle birebir). */
public enum ReportReason {
    SPAM("Spam / reklam"),
    HARASSMENT("Taciz veya zorbalık"),
    HATE_SPEECH("Nefret söylemi"),
    NUDITY("Müstehcen içerik"),
    VIOLENCE("Şiddet"),
    SCAM("Dolandırıcılık"),
    IMPERSONATION("Sahte hesap / taklit"),
    SELF_HARM("Kendine zarar"),
    OTHER("Diğer");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
