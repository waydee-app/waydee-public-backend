package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.storage.StorageService;
import com.waydee.social.api.dto.SocialDtos.MediaResponse;
import com.waydee.social.domain.MediaObject;
import com.waydee.social.infrastructure.MediaObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Görsel yükleme: content-type + magic byte doğrulaması yapılır,
 * nesne UUID adıyla MinIO'ya yazılır ve backend proxy'sinden servis edilir.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    // ══════════════════════════════════════════════════════════════════
    // 🔴 15 Ağu 2026 — VİDEO VE SES (3B mağaza stüdyosu)
    //
    // Kullanıcı: "video yükleyebilecek… mağazada kasanın arkasında bir
    // televizyonda o video oynatılacak… müziği de yükleyebilecekleri alanlar
    // oluştur. Max dosya boyutu koymayı unutma."
    //
    // ⚠️ SINIRLAR TÜRE GÖRE AYRI. Tek bir ortak tavan koymak iki yönden de
    // yanlış olurdu: 10 MB video için gülünç derecede küçük, 200 MB ise
    // profil fotoğrafı için tehlikeli derecede büyük.
    //
    // ⚠️ Bu tavanlar ÜÇ yerde birden tutarlı olmalı:
    //   ① burası, ② `application.yml` → `spring.servlet.multipart.max-file-size`,
    //   ③ `frontend/nginx.conf` → `client_max_body_size`.
    // Biri küçük kalırsa istek buraya HİÇ ulaşmaz ve kullanıcı bizim güzel
    // hata mesajımızı değil, ham bir 413 görür.
    // ══════════════════════════════════════════════════════════════════

    /** Televizyon videosu için tavan. */
    private static final long MAX_VIDEO_BYTES = 60L * 1024 * 1024;

    /** Mağaza müziği için tavan. */
    private static final long MAX_AUDIO_BYTES = 15L * 1024 * 1024;

    /**
     * ⚠️ Yalnız <b>tarayıcının yerel olarak oynatabildiği</b> biçimler.
     * MKV/AVI kabul etmek, yüklenip sonra sahnede sessizce hiç oynamayan bir
     * dosya demekti — kullanıcı için en kötü hata türü.
     */
    private static final Map<String, String> ALLOWED_VIDEO_TYPES = Map.of(
            "video/mp4", ".mp4",
            "video/webm", ".webm");

    private static final Map<String, String> ALLOWED_AUDIO_TYPES = Map.of(
            "audio/mpeg", ".mp3",
            "audio/mp3", ".mp3",
            "audio/ogg", ".ogg",
            "audio/wav", ".wav",
            "audio/webm", ".weba");

    private final MediaObjectRepository mediaRepository;
    private final StorageService storageService;
    private final com.waydee.moderation.application.RestrictionService restrictionService;

    @Transactional
    public MediaResponse upload(UUID ownerId, MultipartFile file) {
        restrictionService.assertAllowed(ownerId, com.waydee.moderation.domain.RestrictedAction.UPLOAD);
        if (file.isEmpty()) {
            throw ApiException.badRequest("Dosya boş");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "Dosya 10 MB'den büyük olamaz");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA,
                    "Yalnızca JPEG, PNG, WebP ve GIF yüklenebilir");
        }

        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException ex) {
            throw ApiException.badRequest("Dosya okunamadı");
        }
        if (!matchesMagicBytes(contentType, header)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA,
                    "Dosya içeriği bildirilen türle uyuşmuyor");
        }

        String objectKey = "%s/%s%s".formatted(LocalDate.now(), UUID.randomUUID(), ALLOWED_TYPES.get(contentType));
        try (InputStream in = file.getInputStream()) {
            storageService.put(objectKey, in, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("Dosya depolamaya aktarılamadı", ex);
        }

        MediaObject media = mediaRepository.save(new MediaObject(ownerId, objectKey, contentType, file.getSize()));
        return new MediaResponse(media.getId(), media.publicUrl(), contentType, file.getSize());
    }

    /**
     * Video yükleme (3B mağazadaki televizyon).
     *
     * <p>⚠️ Sihirli bayt kontrolü <b>görsellerdeki kadar katı değil</b> ve bu
     * bilinçli: MP4 kapsayıcısında imza dosyanın başında sabit bir ofsette
     * durmaz (`ftyp` kutusu genelde 4. bayttadır ama zorunlu değil). Yine de
     * en azından kapsayıcı imzası aranır — content-type'a körü körüne
     * güvenmek, `video/mp4` diye etiketlenmiş bir HTML'i depoya yazmak demekti.
     */
    @Transactional
    public MediaResponse uploadVideo(UUID ownerId, MultipartFile file) {
        return uploadTyped(ownerId, file, ALLOWED_VIDEO_TYPES, MAX_VIDEO_BYTES,
                "Yalnızca MP4 ve WebM video yüklenebilir", "Video 60 MB'den büyük olamaz",
                MediaService::looksLikeVideo);
    }

    /** Ses yükleme (mağaza müziği). */
    @Transactional
    public MediaResponse uploadAudio(UUID ownerId, MultipartFile file) {
        return uploadTyped(ownerId, file, ALLOWED_AUDIO_TYPES, MAX_AUDIO_BYTES,
                "Yalnızca MP3, OGG, WAV ve WebM ses yüklenebilir", "Ses dosyası 15 MB'den büyük olamaz",
                MediaService::looksLikeAudio);
    }

    /**
     * Görsel/video/ses yüklemenin ortak gövdesi.
     *
     * <p>⚠️ Kısıt/limit/mesaj <b>parametre</b>; akış tek. Üç ayrı kopya
     * yazılsaydı kısıtlama (moderasyon kontrolü, sihirli bayt, depolama hatası)
     * er geç birinde unutulurdu.
     */
    private MediaResponse uploadTyped(UUID ownerId, MultipartFile file,
                                      Map<String, String> allowed, long maxBytes,
                                      String typeMessage, String sizeMessage,
                                      java.util.function.BiPredicate<String, byte[]> sniffer) {
        restrictionService.assertAllowed(ownerId, com.waydee.moderation.domain.RestrictedAction.UPLOAD);
        if (file.isEmpty()) {
            throw ApiException.badRequest("Dosya boş");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, sizeMessage);
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowed.containsKey(contentType)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA, typeMessage);
        }

        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(16);
        } catch (IOException ex) {
            throw ApiException.badRequest("Dosya okunamadı");
        }
        if (!sniffer.test(contentType, header)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA,
                    "Dosya içeriği bildirilen türle uyuşmuyor");
        }

        String objectKey = "%s/%s%s".formatted(LocalDate.now(), UUID.randomUUID(), allowed.get(contentType));
        try (InputStream in = file.getInputStream()) {
            storageService.put(objectKey, in, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("Dosya depolamaya aktarılamadı", ex);
        }

        MediaObject media = mediaRepository.save(new MediaObject(ownerId, objectKey, contentType, file.getSize()));
        return new MediaResponse(media.getId(), media.publicUrl(), contentType, file.getSize());
    }

    /** MP4 → `ftyp` kutusu · WebM/Matroska → EBML imzası. */
    private static boolean looksLikeVideo(String contentType, byte[] header) {
        if ("video/webm".equals(contentType)) {
            return startsWithStatic(header, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
        }
        // MP4: ilk 16 baytın herhangi bir yerinde "ftyp" geçmeli.
        return indexOf(header, "ftyp".getBytes()) >= 0;
    }

    private static boolean looksLikeAudio(String contentType, byte[] header) {
        return switch (contentType) {
            // ID3 etiketli ya da ham çerçeve (0xFF 0xFB/0xF3/0xF2…) ile başlar.
            case "audio/mpeg", "audio/mp3" -> startsWithStatic(header, "ID3".getBytes())
                    || (header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0);
            case "audio/ogg" -> startsWithStatic(header, "OggS".getBytes());
            case "audio/wav" -> startsWithStatic(header, "RIFF".getBytes());
            case "audio/webm" -> startsWithStatic(header, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
            default -> false;
        };
    }

    private static boolean startsWithStatic(byte[] data, byte[] prefix) {
        return data.length >= prefix.length
                && Arrays.equals(Arrays.copyOfRange(data, 0, prefix.length), prefix);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * <b>Sunucunun ürettiği bir görseli depoya yazar</b> (V45, yapay zekâ stüdyosu).
     *
     * <p>🔴 {@link #upload} <b>kullanılamaz</b>: o bir {@link MultipartFile}
     * bekler ve <b>moderasyon kısıtını</b> ({@code RestrictedAction.UPLOAD})
     * uygular. Burada dosyayı kullanıcı göndermiyor — biz üretiyoruz; kısıtı
     * ikinci kez uygulamak, üretim isteği zaten kapıdan geçmişken krediyi
     * düşülmüş bir işi son adımda reddetmek olurdu.
     *
     * <p>⚠️ Sihirli bayt kontrolü <b>yine de yapılır</b>: baytlar dış bir
     * servisten (fal.ai) geliyor ve dışarıdan gelen hiçbir şeye türü konusunda
     * güvenilmez. Sağlayıcı bir hata sayfası döndürseydi, kontrol olmadan
     * galeriye "görsel" diye bir HTML yazılırdı.
     *
     * @param ownerId üretimi yapan kullanıcı — galeri ve sahiplik kontrolü buna bakar
     */
    @Transactional
    public MediaObject storeGenerated(UUID ownerId, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("Üretilen görsel boş");
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "Üretilen görsel çok büyük");
        }
        String type = ALLOWED_TYPES.containsKey(contentType) ? contentType : "image/jpeg";
        byte[] header = Arrays.copyOfRange(bytes, 0, Math.min(bytes.length, 12));
        if (!matchesMagicBytes(type, header)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA, "Üretilen dosya bir görsel değil");
        }
        String objectKey = "%s/%s%s".formatted(LocalDate.now(), UUID.randomUUID(), ALLOWED_TYPES.get(type));
        storageService.put(objectKey, new java.io.ByteArrayInputStream(bytes), bytes.length, type);
        return mediaRepository.save(new MediaObject(ownerId, objectKey, type, bytes.length));
    }

    /** Kullanıcının yüklediği tüm görseller — atölyedeki medya kütüphanesi için. */
    @Transactional(readOnly = true)
    public List<MediaResponse> listMine(UUID ownerId) {
        return mediaRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(m -> new MediaResponse(m.getId(), m.publicUrl(), m.getContentType(), m.getSizeBytes()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaObject require(UUID mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> ApiException.notFound("Medya bulunamadı"));
    }

    @Transactional(readOnly = true)
    public void assertOwnedBy(UUID mediaId, UUID ownerId) {
        MediaObject media = require(mediaId);
        if (!media.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden("Bu medya size ait değil");
        }
    }

    public InputStream openStream(MediaObject media) {
        return storageService.get(media.getObjectKey());
    }

    private boolean matchesMagicBytes(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            case "image/gif" -> startsWith(header, "GIF8".getBytes());
            case "image/webp" -> header.length >= 12
                    && startsWith(header, "RIFF".getBytes())
                    && Arrays.equals(Arrays.copyOfRange(header, 8, 12), "WEBP".getBytes());
            default -> false;
        };
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(data, 0, prefix.length), prefix);
    }
}
