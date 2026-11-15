// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.domain.source.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.reeltrip.domain.source.entity.SourceLink;

public interface SourceLinkRepository extends JpaRepository<SourceLink, Long> {

    List<SourceLink> findAllBySource_SourceIdOrderBySourceLinkIdAsc(Long sourceId);

    @Query("select l.source.sourceId from SourceLink l where l.sourceLinkId=:id")
    Optional<Long> parentId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SourceLink l where l.source.sourceId=:id order by l.sourceLinkId")
    List<SourceLink> findAllForUpdate(@Param("id") Long id);
}
