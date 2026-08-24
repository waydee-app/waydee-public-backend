package com.waydee.social.application;

import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.dto.SearchDtos.SearchHit;
import com.waydee.social.api.dto.SearchDtos.SearchResponse;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Birleşik arama: kullanıcılar + daireler.
 *
 * <p>⚠️ Sorgu <b>boş string</b> olarak geçirilir, null olarak DEĞİL:
 * PostgreSQL sürücüsü tipsiz null parametreyi `bytea` çıkarımlar ve
 * `lower(bytea) does not exist` hatası verir (vault'ta kayıtlı tuzak).
 * `LIKE '%%'` her satırı tuttuğu için koşul da basit kalır.
 *
 * <p>Gizlilik: gizli hesaplar sonuçlarda görünür ama <b>maskesiz kimlik
 * vermez</b> — kullanıcı arama sonucundan profile gidince zaten gate devreye
 * girer. Gizlenmiş ve pasif daireler hiç dönmez (repository süzer).
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_PER_TYPE = 8;

    private final UserRepository userRepository;
    private final TerritoryRepository territoryRepository;

    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery, UUID viewerId) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2) {
            // Tek harfle tüm veritabanını taramak anlamsız; istemci de bunu bekler.
            return new SearchResponse(query, List.of(), List.of());
        }

        List<SearchHit> users = userRepository.searchPublic(query, PageRequest.of(0, MAX_PER_TYPE)).stream()
                .filter(u -> !u.getId().equals(viewerId))
                .map(SearchService::toUserHit)
                .toList();

        List<SearchHit> territories = territoryRepository.search(query, PageRequest.of(0, MAX_PER_TYPE)).stream()
                .map(SearchService::toTerritoryHit)
                .toList();

        return new SearchResponse(query, users, territories);
    }

    private static SearchHit toUserHit(User u) {
        return new SearchHit(
                "USER",
                u.getId().toString(),
                u.getUsername(),
                u.getDisplayName(),
                "@" + u.getUsername(),
                MediaUrls.of(u.getAvatarMediaId()),
                u.isPrivateAccount() ? "Gizli hesap" : null,
                null, null);
    }

    private static SearchHit toTerritoryHit(Territory t) {
        return new SearchHit(
                "TERRITORY",
                t.getId().toString(),
                // ⚠️ Daire bir kullanıcı değil — profil adı yok, adres /t/{id}.
                null,
                t.getName(),
                "@" + t.getOwner().getUsername(),
                MediaUrls.of(t.getOwner().getAvatarMediaId()),
                t.getLikeCount() > 0 ? t.getLikeCount() + " beğeni" : null,
                t.getCenter().getX(),
                t.getCenter().getY());
    }
}
