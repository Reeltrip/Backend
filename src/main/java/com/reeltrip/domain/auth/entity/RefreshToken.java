package com.reeltrip.domain.auth.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.reeltrip.domain.user.entity.User;

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
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RefreshToken() { }

    /** 만료 시각은 서비스가 기존 refresh-token-days 설정과 Clock으로 계산해요. */
    public static RefreshToken create(User user, String token, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        Objects.requireNonNull(issuedAt);
        Objects.requireNonNull(expiresAt);
        if (token == null || token.isBlank() || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("리프레시 토큰과 만료 시각을 확인해 주세요.");
        }
        RefreshToken result = new RefreshToken();
        result.user = Objects.requireNonNull(user);
        result.token = token;
        result.createdAt = issuedAt;
        result.expiresAt = expiresAt;
        return result;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public Long getRefreshTokenId() { return refreshTokenId; }
    public User getUser() { return user; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
