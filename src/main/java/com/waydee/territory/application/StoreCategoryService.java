package com.waydee.territory.application;

import com.waydee.common.error.ApiException;
import com.waydee.territory.api.dto.StoreCategoryDtos.AdminCreateCategoryRequest;
import com.waydee.territory.api.dto.StoreCategoryDtos.AdminUpdateCategoryRequest;
import com.waydee.territory.api.dto.StoreCategoryDtos.StoreCategoryResponse;
import com.waydee.territory.domain.StoreCategory;
import com.waydee.territory.infrastructure.StoreCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Mağaza kategorilerinin okunması ve yönetimi (V52).
 *
 * <p>⚠️ Kategori <b>territory</b> modülünde durur, ayrı bir modülde değil:
 * kategori bir mağazanın özelliğidir ve mağaza = bölgedir. Ayrı modül,
 * {@code territory → kategori} bağımlılığını modüller arası bir çağrıya
 * çevirirdi; kazancı yoktu.
 */
@Service
@RequiredArgsConstructor
public class StoreCategoryService {

    private final StoreCategoryRepository repository;
    /**
     * 🔴 <b>Kullanıcının cevabı BURADAN yazılır, {@code identity}'den değil.</b>
     *
     * <p>Doğrulama kategoriyi okumayı gerektiriyor ve kategori bu modülün
     * varlığı. Ucu {@code identity}'ye koymak {@code identity → territory}
     * bağımlılığı demekti ve o yön <b>çevrim</b> üretir — {@code territory}
     * zaten {@code identity}'yi tanıyor ({@code PlanService}). Aynı gerekçe
     * {@code payment → territory} kararında da yazılıydı (bkz. vault 02).
     */
    private final com.waydee.identity.infrastructure.UserRepository userRepository;

    /** Seçim listeleri — yalnız aktif olanlar. */
    @Transactional(readOnly = true)
    public List<StoreCategoryResponse> listActive() {
        return repository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(StoreCategoryResponse::from)
                .toList();
    }

    /** Yönetim listesi — pasifler de görünür, yoksa geri açılamazlardı. */
    @Transactional(readOnly = true)
    public List<StoreCategoryResponse> listAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(StoreCategoryResponse::from)
                .toList();
    }

    /**
     * Kimliği çözer.
     *
     * <p>🔴 <b>Pasif kategori SEÇİLEMEZ.</b> Pasif olan "artık teklif edilmiyor"
     * demektir; kimliğini bilen bir istemcinin onu yine de yazabilmesi,
     * yöneticinin kapattığı kategoriyi kapatmamış olurdu. Ama <b>zaten
     * yazılmış</b> satırlar okunmaya devam eder — bu ikisi farklı yönlerdir.
     */
    @Transactional(readOnly = true)
    public StoreCategory requireSelectable(UUID id) {
        StoreCategory category = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Kategori bulunamadı"));
        if (!category.isActive()) {
            throw ApiException.badRequest("Bu kategori artık seçilemiyor");
        }
        return category;
    }

    /**
     * <b>Kayıt sonrası popup'ın cevabı</b> (V52).
     *
     * <p>⚠️ {@code categoryId == null} geçerli bir cevaptır: *"geç"*. O
     * durumda kategori yazılmaz ama <b>soruldu damgası basılır</b> — yoksa
     * popup her açılışta yeniden çıkardı ({@code User#markStoreCategoryAsked}).
     *
     * <p>⚠️ Bu çağrı kullanıcının <b>mağazasına dokunmaz</b>. İkisi farklı
     * sorulardır: buradaki cevap mağaza açılırken tohumdur, mağazanın bugünkü
     * kategorisi mağaza düzenleme ekranından değişir. Peşinden sürüklemek,
     * ayarlardan alanını değiştiren kullanıcının mağaza kategorisini de
     * habersizce değiştirirdi.
     */
    @Transactional
    public void chooseForUser(UUID userId, UUID categoryId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        if (categoryId != null) {
            user.setStoreCategoryId(requireSelectable(categoryId).getId());
        }
        user.markStoreCategoryAsked();
    }

    // ------------------------------------------------------------------ admin

    @Transactional
    public StoreCategoryResponse create(AdminCreateCategoryRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.badRequest("Bu kod zaten kullanılıyor: " + code);
        }
        StoreCategory category = repository.save(new StoreCategory(
                code,
                request.name().trim(),
                request.icon().trim(),
                request.color().trim(),
                request.sortOrder() != null ? request.sortOrder() : 50));
        return StoreCategoryResponse.from(category);
    }

    @Transactional
    public StoreCategoryResponse update(UUID id, AdminUpdateCategoryRequest request) {
        StoreCategory category = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Kategori bulunamadı"));
        if (request.name() != null && !request.name().isBlank()) {
            category.setName(request.name().trim());
        }
        if (request.icon() != null && !request.icon().isBlank()) {
            category.setIcon(request.icon().trim());
        }
        if (request.color() != null && !request.color().isBlank()) {
            category.setColor(request.color().trim());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return StoreCategoryResponse.from(category);
    }
}
