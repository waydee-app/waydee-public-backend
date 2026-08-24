package com.waydee.identity.domain;

/**
 * Profilde gösterilebilen sosyal medya platformları.
 *
 * Sıra, kullanıcı bir sıralama vermediğinde varsayılan gösterim sırasıdır.
 * Yeni platform eklemek için buraya bir sabit + V16 migration'daki CHECK'i
 * genişleten yeni bir migration gerekir (mevcut migration DEĞİŞTİRİLMEZ).
 */
public enum SocialPlatform {
    WEBSITE,
    INSTAGRAM,
    X,
    FACEBOOK,
    YOUTUBE,
    TIKTOK,
    SNAPCHAT,
    LINKEDIN,
    TELEGRAM,
    GITHUB
}
