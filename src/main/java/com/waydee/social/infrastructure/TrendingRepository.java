package com.waydee.social.infrastructure;

import com.waydee.social.domain.TrendingEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TrendingRepository extends JpaRepository<TrendingEntry, UUID> {

    @Query("select e from TrendingEntry e where e.subjectType = :type order by e.rank asc")
    List<TrendingEntry> findBySubjectTypeOrderByRankAsc(String type);
}
