// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 액세스 토큰 발급·검증. 테스트에서도 이 클래스로 토큰을 직접 만들어 써요.
 * 리프레시 토큰(DB 저장)은 BE 1이 domain/auth에서 만들어요.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
            Clock clock
    ) {
        // HS256은 32바이트 이상 키가 필요해요. 짧으면 서버가 뜨지 않게 여기서 막아요.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.clock = clock;
    }

    public String createAccessToken(Long userId) {
        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(Date.from(now.toInstant().plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** 서명이 맞고 만료되지 않았으면 userId, 아니면 빈 값. */
    public Optional<Long> parseUserId(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // JJWT의 경계 비교와 별개로 만료 시각부터는 사용하지 못하게 해요.
            if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            long userId = Long.parseLong(claims.getSubject());
            return userId > 0 ? Optional.of(userId) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
