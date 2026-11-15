// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.domain.source.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.reeltrip.domain.source.entity.Source;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findAllByUser_UserId(Long userId, Pageable pageable);

    Optional<Source> findBySourceIdAndUser_UserId(Long sourceId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Source s where s.sourceId=:id")
    Optional<Source> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Source s where s.sourceId=:id and s.user.userId=:userId")
    Optional<Source> findOwnedForUpdate(@Param("id") Long id,@Param("userId") Long userId);
}
