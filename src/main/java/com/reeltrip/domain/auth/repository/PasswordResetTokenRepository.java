package com.reeltrip.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.reeltrip.domain.auth.entity.PasswordResetToken;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserUserIdOrderByCreatedAtDescPasswordResetTokenIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reset from PasswordResetToken reset where reset.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("delete from PasswordResetToken reset where reset.user.userId = :userId")
    void deleteAllByUserUserId(@Param("userId") Long userId);

    @Query("select reset.user.userId from PasswordResetToken reset where reset.tokenHash = :hash")
    Optional<Long> userIdByHash(@Param("hash") String hash);
}
