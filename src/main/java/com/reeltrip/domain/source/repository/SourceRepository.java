// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.domain.source.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.source.entity.Source;

public interface SourceRepository extends JpaRepository<Source, Long> {

    Optional<Source> findBySourceIdAndUser_UserId(Long sourceId, Long userId);
}
