package com.waydee.marketplace.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waydee.common.error.ApiException;
import com.waydee.marketplace.api.dto.MarketplaceDtos.FormSchemaInput;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingRequest;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ResolvedField;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ResolvedForm;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ResolvedQuestion;
import com.waydee.marketplace.domain.FormSchema;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Başvuru formunun tek otoritesi: şemayı çözer, saklar ve gelen başvuruyu
 * <b>sunucuda</b> şemaya göre doğrular.
 *
 * <p>⚠️ Doğrulama neden sunucuda: istemci formu şemadan çiziyor ama bir alanın
 * "zorunlu" olması istemci kararına bırakılamaz — istek doğrudan da atılabilir.
 * Aynı sebeple <b>kapalı alanlar temizlenir</b>: admin "fiyat sorma" dediyse
 * gönderilen fiyat kaydedilmez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormSchemaService {

    /** Alan adları — kullanıcıya gösterilecek varsayılan etiketler. */
    private static final Map<MarketplaceKind.Field, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(MarketplaceKind.Field.TAGLINE, "Tek cümlelik tanıtım");
        LABELS.put(MarketplaceKind.Field.LOGO, "Logo");
        LABELS.put(MarketplaceKind.Field.COVER, "Kapak görseli");
        LABELS.put(MarketplaceKind.Field.GALLERY, "Görseller");
        LABELS.put(MarketplaceKind.Field.WEBSITE, "Web sitesi");
        LABELS.put(MarketplaceKind.Field.CONTACT_EMAIL, "İletişim e-postası");
        LABELS.put(MarketplaceKind.Field.CONTACT_PHONE, "Telefon");
        LABELS.put(MarketplaceKind.Field.STAGE, "Aşama");
        LABELS.put(MarketplaceKind.Field.FOUNDED_YEAR, "Kuruluş yılı");
        LABELS.put(MarketplaceKind.Field.TEAM_SIZE, "Ekip büyüklüğü");
        LABELS.put(MarketplaceKind.Field.LOOKING_FOR, "Ne arıyorsunuz?");
        LABELS.put(MarketplaceKind.Field.STARTS_AT, "Başlangıç");
        LABELS.put(MarketplaceKind.Field.ENDS_AT, "Bitiş");
        LABELS.put(MarketplaceKind.Field.LOCATION, "Buluşma noktası");
        LABELS.put(MarketplaceKind.Field.CAPACITY, "Kontenjan");
        LABELS.put(MarketplaceKind.Field.PRICE, "Fiyat");
        LABELS.put(MarketplaceKind.Field.CONDITION, "Ürün durumu");
    }

    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------ şema

    /** Adminin gönderdiği şemayı JSON'a çevirir (null → varsayılan kullanılsın diye null döner). */
    public String serialize(FormSchemaInput input) {
        if (input == null || (isEmpty(input.fields()) && isEmpty(input.questions()))) {
            return null;
        }
        Set<String> seen = new HashSet<>();
        if (input.questions() != null) {
            for (var q : input.questions()) {
                if (!seen.add(q.key())) {
                    throw ApiException.badRequest("Aynı soru anahtarı iki kez kullanılamaz: " + q.key());
                }
                if ("SELECT".equals(q.type()) && isEmpty(q.options())) {
                    throw ApiException.badRequest("“" + q.label() + "” için en az bir seçenek girin");
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            throw ApiException.badRequest("Form şeması kaydedilemedi");
        }
    }

    /** Pazarın çözülmüş formu — admin şema vermemişse türün varsayılanı. */
    public ResolvedForm resolve(Marketplace marketplace) {
        FormSchema schema = read(marketplace);
        List<ResolvedField> fields = new ArrayList<>();
        for (MarketplaceKind.Field f : MarketplaceKind.Field.values()) {
            var cfg = schema.fields() == null ? null : schema.fields().stream()
                    .filter(c -> c.field().equals(f.name())).findFirst().orElse(null);
            boolean enabled = cfg != null ? cfg.enabled() : marketplace.getKind().defaultFields().contains(f);
            fields.add(new ResolvedField(
                    f.name(),
                    enabled,
                    cfg != null && cfg.required(),
                    cfg != null && cfg.label() != null && !cfg.label().isBlank() ? cfg.label() : LABELS.get(f),
                    cfg != null ? cfg.help() : null));
        }
        List<ResolvedQuestion> questions = (schema.questions() == null ? List.<FormSchema.Question>of() : schema.questions())
                .stream()
                .map(q -> new ResolvedQuestion(q.key(), q.label(), q.type(), q.required(),
                        q.help(), q.options(), q.maxLength()))
                .toList();
        return new ResolvedForm(
                marketplace.getKind().name(),
                marketplace.getKind().label(),
                marketplace.getApplicationNote(),
                fields, questions);
    }

    private FormSchema read(Marketplace m) {
        if (m.getFormSchema() == null || m.getFormSchema().isBlank()) {
            return FormSchema.defaultFor(m.getKind());
        }
        try {
            return objectMapper.readValue(m.getFormSchema(), FormSchema.class);
        } catch (Exception e) {
            // Bozuk şema formu KİLİTLEMEZ — varsayılana düşülür ve loglanır.
            log.warn("Pazar {} için form şeması okunamadı, varsayılana düşülüyor", m.getId(), e);
            return FormSchema.defaultFor(m.getKind());
        }
    }

    // ------------------------------------------------------------ doğrulama

    /**
     * Başvuruyu şemaya göre doğrular ve <b>kapalı alanları temizlenmiş</b> bir
     * sonuç döndürür.
     *
     * @return kaydedilecek serbest cevaplar (JSON); soru yoksa null
     */
    public String validateAndBuildCustomFields(Marketplace marketplace, ListingRequest request) {
        ResolvedForm form = resolve(marketplace);
        Map<String, Boolean> enabled = new HashMap<>();
        Map<String, Boolean> required = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (ResolvedField f : form.fields()) {
            enabled.put(f.field(), f.enabled());
            required.put(f.field(), f.required());
            labels.put(f.field(), f.label());
        }

        requireIf(required, labels, "STARTS_AT", request.startsAt());
        requireIf(required, labels, "ENDS_AT", request.endsAt());
        requireIf(required, labels, "LOCATION", blankToNull(request.locationLabel()));
        requireIf(required, labels, "CAPACITY", request.capacity());
        requireIf(required, labels, "PRICE", request.price());
        requireIf(required, labels, "CONDITION", blankToNull(request.conditionCode()));
        requireIf(required, labels, "WEBSITE", blankToNull(request.website()));
        requireIf(required, labels, "CONTACT_EMAIL", blankToNull(request.contactEmail()));
        requireIf(required, labels, "CONTACT_PHONE", blankToNull(request.contactPhone()));
        requireIf(required, labels, "STAGE", blankToNull(request.stage()));
        requireIf(required, labels, "FOUNDED_YEAR", request.foundedYear());
        requireIf(required, labels, "TEAM_SIZE", request.teamSize());
        requireIf(required, labels, "LOOKING_FOR", blankToNull(request.lookingFor()));
        requireIf(required, labels, "LOGO", request.logoMediaId());
        requireIf(required, labels, "COVER", request.coverMediaId());

        if (Boolean.TRUE.equals(required.get("GALLERY")) && isEmpty(request.galleryMediaIds())) {
            throw ApiException.badRequest("“" + labels.get("GALLERY") + "” zorunludur");
        }

        // Tarih tutarlılığı — DB kısıtı var ama hata mesajı anlamlı olsun.
        if (request.startsAt() != null && request.endsAt() != null
                && request.endsAt().isBefore(request.startsAt())) {
            throw ApiException.badRequest("Bitiş, başlangıçtan önce olamaz");
        }

        // ---- serbest sorular
        Map<String, String> answers = request.customFields() == null ? Map.of() : request.customFields();
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (ResolvedQuestion q : form.questions()) {
            String value = answers.get(q.key());
            boolean empty = value == null || value.isBlank();
            if (q.required() && empty) {
                throw ApiException.badRequest("“" + q.label() + "” zorunludur");
            }
            if (empty) {
                continue;
            }
            if (q.maxLength() != null && value.length() > q.maxLength()) {
                throw ApiException.badRequest("“" + q.label() + "” en fazla " + q.maxLength() + " karakter olabilir");
            }
            if ("SELECT".equals(q.type()) && q.options() != null && !q.options().contains(value)) {
                throw ApiException.badRequest("“" + q.label() + "” için geçersiz seçenek");
            }
            if ("NUMBER".equals(q.type())) {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest("“" + q.label() + "” sayı olmalı");
                }
            }
            cleaned.put(q.key(), value.trim());
        }
        // Şemada olmayan anahtarlar SESSİZCE ATILIR — istemci uydurma alan yazamaz.
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw ApiException.badRequest("Cevaplar kaydedilemedi");
        }
    }

    /** Bir alan bu pazarda kapalıysa gelen değer yok sayılır. */
    public boolean isFieldEnabled(Marketplace marketplace, MarketplaceKind.Field field) {
        return resolve(marketplace).fields().stream()
                .filter(f -> f.field().equals(field.name()))
                .findFirst()
                .map(ResolvedField::enabled)
                .orElse(false);
    }

    public Map<String, String> readCustomFields(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static void requireIf(Map<String, Boolean> required, Map<String, String> labels,
                                  String field, Object value) {
        if (Boolean.TRUE.equals(required.get(field)) && value == null) {
            throw ApiException.badRequest("“" + labels.getOrDefault(field, field) + "” zorunludur");
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
