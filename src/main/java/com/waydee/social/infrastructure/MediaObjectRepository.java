package com.waydee.social.infrastructure;

import com.waydee.social.domain.MediaObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaObjectRepository extends JpaRepository<MediaObject, UUID> {

    List<MediaObject> findByIdInAndOwnerId(List<UUID> ids, UUID ownerId);

    List<MediaObject> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
