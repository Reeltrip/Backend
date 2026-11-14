package com.reeltrip.domain.auth.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

import com.reeltrip.domain.user.entity.User;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_token_id")
    private Long passwordResetTokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PasswordResetToken() { }

    /** 원문 토큰은 객체 필드에도 남기지 않아요. */
    public static PasswordResetToken create(User user, String rawToken, LocalDateTime issuedAt) {
        PasswordResetToken result = new PasswordResetToken();
        result.user = Objects.requireNonNull(user);
        result.tokenHash = hashToken(rawToken);
        result.createdAt = Objects.requireNonNull(issuedAt);
        result.expiresAt = issuedAt.plusMinutes(30);
        return result;
    }

    public static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("재설정 토큰이 필요해요.");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256을 사용할 수 없어요.", error);
        }
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /** 동시 소비는 서비스 트랜잭션에서 레포지토리의 잠금 조회와 함께 처리해요. */
    public void markUsed(LocalDateTime now) {
        if (isUsed()) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }
        if (isExpired(now)) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_EXPIRED);
        }
        usedAt = now;
    }

    public Long getPasswordResetTokenId() { return passwordResetTokenId; }
    public User getUser() { return user; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
