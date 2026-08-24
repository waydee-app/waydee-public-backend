package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.application.TagSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <b>Etiket noktası önerisi</b> — fotoğraf yüklendikten sonra çağrılır.
 *
 * <p>🔒 <b>POST</b>, GET değil: analiz hesap yapar (görsel indirilir ve
 * işlenir). GET yapmak, önbelleklerin ve ön-getirmelerin farkında olmadan
 * işlemci yakmasına yol açardı.
 *
 * <p>🔒 Premium kapısı serviste; yetkisiz çağrı <b>403 PLAN_LIMIT_REACHED</b>
 * döner ve arayüz yükseltme kartını açar.
 */
@Tag(name = "Posts", description = "Gönderi, beğeni ve yorumlar")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class TagSuggestionController {

    private final TagSuggestionService tagSuggestionService;

    /**
     * ⚠️ Görsel <b>istekle birlikte</b> gelir (multipart), medya kimliğiyle
     * değil: gönderi akışında fotoğraf kaydetme anına kadar yüklenmiyor.
     * Analiz için önce depoya yazmak, vazgeçilen her denemede çöp dosya
     * bırakırdı.
     */
    @Operation(summary = "Görselde etiket konabilecek noktaları öner (Premium)")
    @PostMapping(value = "/tag-suggestions", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<TagSuggestionService.Suggestion> suggest(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser principal) throws java.io.IOException {
        return tagSuggestionService.suggest(file.getInputStream(), principal.id());
    }
}
