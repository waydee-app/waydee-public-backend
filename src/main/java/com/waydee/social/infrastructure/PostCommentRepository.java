package com.waydee.social.infrastructure;

import com.waydee.social.domain.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    @EntityGraph(attributePaths = "author")
    Page<PostComment> findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID postId, Pageable pageable);
}
