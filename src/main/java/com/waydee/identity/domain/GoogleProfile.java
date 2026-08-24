package com.waydee.identity.domain;

/**
 * Google {@code id_token} içinden okunan, bize gereken alanlar.
 *
 * @param sub           Google hesabının <b>değişmeyen</b> kimliği — eşleme buna göre yapılır
 * @param email         hesabın adresi
 * @param emailVerified Google bu adresin sahipliğini doğruladı mı
 *                      (⚠️ {@code false} ise mevcut bir hesaba <b>bağlanamaz</b>)
 * @param name          görünen ad (boş olabilir)
 * @param picture       profil fotoğrafı adresi (şu an kullanılmıyor — bkz. vault "sonraki adımlar")
 */
public record GoogleProfile(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String picture
) {
}
