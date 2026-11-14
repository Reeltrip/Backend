package com.reeltrip.domain.auth.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** 발송 시각과 시도 횟수를 DB에 두어 인스턴스 간 같은 인증 상태를 사용해요. */
@Entity
@Table(name = "email_code", indexes = @Index(name = "idx_email_code_email_created", columnList = "email, created_at"))
public class EmailCode {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_code_id")
    private Long emailCodeId;

    @Column(nullable = false, length = 255)
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 6)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected EmailCode() { }

    public static EmailCode create(String email, String code, LocalDateTime sentAt) {
        Objects.requireNonNull(email);
        Objects.requireNonNull(sentAt);
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("인증번호는 숫자 6자리여야 해요.");
        }
        EmailCode result = new EmailCode();
        result.email = email;
        result.code = code;
        result.createdAt = sentAt;
        result.expiresAt = sentAt.plusMinutes(5);
        return result;
    }

    public boolean matches(String submittedCode) {
        return code.equals(submittedCode);
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isAttemptsExceeded() {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public boolean canResend(LocalDateTime now) {
        return !now.isBefore(createdAt.plusSeconds(60));
    }

    public boolean canSignUp(LocalDateTime now) {
        return isVerified && !isAttemptsExceeded() && now.isBefore(createdAt.plusMinutes(30));
    }

    public void recordFailedAttempt() {
        if (attemptCount < MAX_ATTEMPTS) {
            attemptCount++;
        }
    }

    public void markVerified(LocalDateTime now) {
        if (isAttemptsExceeded()) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }
        if (isExpired(now)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        isVerified = true;
    }

    public Long getEmailCodeId() { return emailCodeId; }
    public String getEmail() { return email; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isVerified() { return isVerified; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
