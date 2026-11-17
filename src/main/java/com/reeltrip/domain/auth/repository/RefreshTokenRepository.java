package com.reeltrip.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.reeltrip.domain.auth.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refresh from RefreshToken refresh where refresh.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    // 현재 값을 직접 삭제해 MySQL의 이전 조회 스냅샷 뒤 발급된 refresh도 폐기해요.
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken refresh where refresh.user.userId = :userId")
    void deleteAllByUserUserId(@Param("userId") Long userId);

    // 로그아웃 (#79). 지금 로그인의 토큰 하나만 지워요.
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken refresh where refresh.token = :token")
    int deleteByToken(@Param("token") String token);

    @Query("select refresh.user.userId from RefreshToken refresh where refresh.token = :token")
    Optional<Long> userIdByToken(@Param("token") String token);
}
