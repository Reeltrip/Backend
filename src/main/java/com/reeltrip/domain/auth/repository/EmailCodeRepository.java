package com.reeltrip.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reeltrip.domain.auth.entity.EmailCode;

import jakarta.persistence.LockModeType;

public interface EmailCodeRepository extends JpaRepository<EmailCode, Long> {

    Optional<EmailCode> findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc(String email);

    /** 호출자는 쓰기 트랜잭션을 열어야 해요. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from EmailCode code where code.emailCodeId = :emailCodeId")
    Optional<EmailCode> findByEmailCodeIdForUpdate(@Param("emailCodeId") Long emailCodeId);

    void deleteAllByEmail(String email);
}
