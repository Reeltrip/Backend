package com.reeltrip.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtProviderTest {

    private static final String SECRET = "test-only-jwt-key-01234567890123456789";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T01:00:00Z"), ZoneOffset.UTC);
    private final JwtProvider provider = new JwtProvider(SECRET, 30, CLOCK);

    @Test
    @DisplayName("서명이 맞아도 만료 시각 없는 JWT는 사용할 수 없다.")
    void missingExpiryRejected() {
        // given
        String token = Jwts.builder().subject("1").signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        // when / then
        assertThat(provider.parseUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("서명이 맞아도 양수가 아닌 회원 ID는 인증에 사용할 수 없다.")
    void nonPositiveUserIdRejected() {
        // given
        String token = Jwts.builder().subject("-1").expiration(Date.from(CLOCK.instant().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        // when / then
        assertThat(provider.parseUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("같은 회원에게 같은 시각에 발급해도 액세스 토큰은 서로 다르다.")
    void simultaneousIssuesAreDistinct() {
        // given / when
        String first = provider.createAccessToken(1L);
        String second = provider.createAccessToken(1L);
        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(provider.parseUserId(first)).contains(1L);
        assertThat(provider.parseUserId(second)).contains(1L);
    }
}
