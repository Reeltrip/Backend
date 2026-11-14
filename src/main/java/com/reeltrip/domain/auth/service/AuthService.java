package com.reeltrip.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.auth.dto.LoginRequest;
import com.reeltrip.domain.auth.dto.LoginResponse;
import com.reeltrip.domain.auth.dto.TokenResponse;
import com.reeltrip.domain.auth.entity.RefreshToken;
import com.reeltrip.domain.auth.repository.RefreshTokenRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.JwtProvider;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenCodec tokenCodec;
    private final Clock clock;
    private final long refreshTokenDays;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, RefreshTokenCodec tokenCodec, Clock clock,
                       @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        if (refreshTokenDays <= 0) {
            throw new IllegalArgumentException("리프레시 토큰 유효기간은 1일 이상이어야 해요.");
        }
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.tokenCodec = tokenCodec;
        this.clock = clock;
        this.refreshTokenDays = refreshTokenDays;
        // 없는 이메일도 BCrypt 검사를 거쳐 계정 유무에 따른 응답 시간 차이를 줄여요.
        this.dummyPasswordHash = passwordEncoder.encode(tokenCodec.generate());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        User user = users.findByEmail(request.email()).orElse(null);
        String encodedPassword = user == null ? dummyPasswordHash : user.getPassword();
        boolean matches = passwordEncoder.matches(request.password(), encodedPassword);
        if (user == null || !matches) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String rawRefreshToken = tokenCodec.generate();
        refreshTokens.save(RefreshToken.create(user, tokenCodec.hash(rawRefreshToken), now, now.plusDays(refreshTokenDays)));
        return new LoginResponse(user.getUserId(), user.getNickname(),
                jwtProvider.createAccessToken(user.getUserId()), rawRefreshToken);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        if (!tokenCodec.isValidFormat(rawRefreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String digest = tokenCodec.hash(rawRefreshToken);
        RefreshToken stored = refreshTokens.findByTokenForUpdate(digest)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (stored.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String nextRefreshToken = tokenCodec.generate();
        // 행 잠금 안에서 이전 해시를 교체해 같은 토큰의 동시 재발급을 한 번만 허용해요.
        stored.rotateToken(tokenCodec.hash(nextRefreshToken));
        return new TokenResponse(jwtProvider.createAccessToken(stored.getUser().getUserId()), nextRefreshToken);
    }
}
