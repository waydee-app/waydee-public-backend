package com.waydee.identity.infrastructure;

import com.waydee.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * Google girişinin BİRİNCİL eşleme yolu.
     *
     * <p>E-posta ile değil {@code sub} ile aranır: kullanıcı Google hesabının
     * adresini değiştirse bile aynı Waydee hesabına düşer.
     */
    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Aktif yonetici sayisi - son yoneticinin silinmesini engellemek icin.
     *
     * <p>UYARI: yalniz ACTIVE sayilir; askiya alinmis ya da silinmis bir
     * yonetici panele giremeyecegi icin "son yonetici" korumasinda sayilmaz.
     */
    long countByRoleAndStatus(com.waydee.identity.domain.Role role,
                              com.waydee.identity.domain.UserStatus status);

    /**
     * Süresi dolmuş <b>ücretli</b> hesaplar (V35; V37'de PREMIUM de kapsandı) —
     * saatlik süpürme bunları FREE'ye düşürür.
     *
     * <p>🔴 Eskiden yalnız {@code plan = PRO} taranıyordu; PREMIUM eklenince o
     * sorgu süresi dolmuş PREMIUM satırlarını <b>hiç görmezdi</b> ve kullanıcı
     * yönetim listesinde ömür boyu PREMIUM görünürdü.
     *
     * <p>⚠️ {@code planExpiresAt IS NULL} olan satırlar buraya <b>girmez</b>;
     * onlar okuma tarafında zaten geçersiz sayılır ({@code User#isPlanActive}) ve
     * süpürmede sessizce silinmeleri bozuk veriyi gizlerdi.
     */
    java.util.List<User> findByPlanInAndPlanExpiresAtBefore(
            java.util.Collection<com.waydee.identity.domain.UserPlan> plans, java.time.Instant before);

    @Query("""
            SELECT u FROM User u
            WHERE lower(u.username) LIKE lower(concat('%', :query, '%'))
               OR lower(u.email) LIKE lower(concat('%', :query, '%'))
               OR lower(u.displayName) LIKE lower(concat('%', :query, '%'))
            """)
    Page<User> search(@Param("query") String query, Pageable pageable);

    /**
     * Herkese açık arama.
     *
     * ⚠️ Yukarıdaki {@link #search} <b>e-posta ile de eşleşir</b> ve YALNIZ yönetim
     * içindir. Kullanıcı tarafındaki arama e-postayı hiç görmemelidir; aksi halde
     * bir e-posta adresini yazıp o hesabı bulmak (enumeration) mümkün olurdu.
     * Askıya alınmış hesaplar da sonuçlarda çıkmaz.
     */
    /*
     * 🔴 17 Ağu 2026 — YÖNETİCİ ELEMESİ BURAYA DA KONDU (88. turun eksiği).
     *
     * Filtre yalnız {@code status = ACTIVE} idi; {@code User.hasPublicProfile()}
     * ise ayrıca {@code role != ADMIN} istiyor. İki filtrenin ayrışması
     * <b>ölçülebilir bir hataya</b> yol açıyordu: arama yönetici hesabını
     * LİSTELİYOR, kullanıcı tıklıyor, {@code GET /users/{id}} 404 dönüyor ve
     * {@code UserRedirect} sessizce <b>ana sayfaya</b> atıyordu. Kullanıcı için
     * bu "arama bozuk" demekti.
     *
     * Ayrıca bir sızıntı: yöneticinin kullanıcı adı ve görünen adı aramada
     * herkese görünüyordu — 88. turda tam olarak bu kapatılmıştı, ama arama
     * o gün sayılmamış BEŞİNCİ yüzeydi.
     *
     * ⚠️ Kural ROLE bakar, ADA değil (vault kuralı).
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.status = com.waydee.identity.domain.UserStatus.ACTIVE
              AND u.role <> com.waydee.identity.domain.Role.ADMIN
              AND (lower(u.username) LIKE lower(concat('%', :query, '%'))
                   OR lower(u.displayName) LIKE lower(concat('%', :query, '%')))
            ORDER BY u.username ASC
            """)
    java.util.List<User> searchPublic(@Param("query") String query, Pageable pageable);

    /**
     * <b>Arama motoruna açılabilecek profiller</b> — site haritasının kaynağı.
     *
     * <p>⚠️ Üç kapı birden: hesap <b>ACTIVE</b>, <b>gizli değil</b> ve
     * <b>e-postası doğrulanmış</b>. Doğrulanmamış hesaplar bilinçli dışarıda:
     * site haritası Google'a "bu adres kalıcıdır" sözü verir, oysa doğrulanmamış
     * bir kayıt her an temizlenebilir — 404'e düşen bir sitemap girdisi tarama
     * bütçesini yakar ve alan adının güvenini düşürür.
     *
     * <p>⚠️ Sıralama {@code updatedAt DESC}: sitemap sayfalara bölündüğünde ilk
     * sayfa her zaman en taze profilleri taşır.
     *
     * <p>⚠️ <b>Yönetici hesapları dışarıda</b> (ölçüldü: ilk site haritasında
     * {@code /admin} çıktı). Yönetim hesabı bir vitrin değildir; indekslenmesi
     * hem anlamsız bir sonuç üretir hem de hesabın adını ilan eder.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.status = com.waydee.identity.domain.UserStatus.ACTIVE
              AND u.privateAccount = false
              AND u.emailVerified = true
              AND u.role = com.waydee.identity.domain.Role.USER
            ORDER BY u.updatedAt DESC
            """)
    Page<User> findIndexableProfiles(Pageable pageable);
}
