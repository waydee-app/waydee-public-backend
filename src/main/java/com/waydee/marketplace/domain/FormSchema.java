package com.waydee.marketplace.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Başvuru formunun tanımı — adminin tasarladığı şema.
 *
 * <p>İki parçası var:
 * <ul>
 *   <li><b>fields</b>: hazır alanlardan hangileri görünsün ve hangileri zorunlu.</li>
 *   <li><b>questions</b>: adminin kendi yazdığı ek sorular (metin, sayı, seçim,
 *       onay kutusu). Cevapları stant kaydında {@code customFields} JSONB'sinde
 *       anahtar-değer olarak durur.</li>
 * </ul>
 *
 * <p>Şema {@code null} bırakılırsa türün varsayılanı uygulanır
 * ({@link MarketplaceKind#defaultFields()}) — admin hiçbir şey ayarlamadan da
 * doğru form çıkar.
 */
public record FormSchema(List<FieldConfig> fields, List<Question> questions) {

    public record FieldConfig(String field, boolean enabled, boolean required, String label, String help) {
    }

    /**
     * @param type TEXT | TEXTAREA | NUMBER | SELECT | CHECKBOX | DATE
     * @param key  cevapların JSONB'de saklanacağı anahtar
     */
    public record Question(
            String key,
            String label,
            String type,
            boolean required,
            String help,
            List<String> options,
            Integer maxLength
    ) {
    }

    public static final Set<String> QUESTION_TYPES =
            Set.of("TEXT", "TEXTAREA", "NUMBER", "SELECT", "CHECKBOX", "DATE");

    /** Türün varsayılanından şema üretir (admin hiç dokunmadıysa). */
    public static FormSchema defaultFor(MarketplaceKind kind) {
        List<FieldConfig> fields = new ArrayList<>();
        Set<MarketplaceKind.Field> on = new LinkedHashSet<>(kind.defaultFields());
        for (MarketplaceKind.Field f : MarketplaceKind.Field.values()) {
            boolean enabled = on.contains(f);
            // Varsayılanda hiçbir opsiyonel alan ZORUNLU değildir; zorunluluk
            // adminin bilinçli tercihidir (başvuru engeli yaratmamak için).
            fields.add(new FieldConfig(f.name(), enabled, false, null, null));
        }
        return new FormSchema(fields, List.of());
    }

    /** Bir alan bu şemada açık mı? */
    public boolean isEnabled(MarketplaceKind.Field field) {
        return fields == null || fields.stream()
                .filter(f -> f.field().equals(field.name()))
                .findFirst()
                .map(FieldConfig::enabled)
                .orElse(false);
    }

    public boolean isRequired(MarketplaceKind.Field field) {
        return fields != null && fields.stream()
                .filter(f -> f.field().equals(field.name()))
                .findFirst()
                .map(FieldConfig::required)
                .orElse(false);
    }
}
