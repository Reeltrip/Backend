package com.reeltrip.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.auth.dto.LoginRequest;
import com.reeltrip.domain.auth.dto.LoginResponse;
import com.reeltrip.domain.auth.dto.SignupRequest;
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
    private final EmailCodeService emailCodeService;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, RefreshTokenCodec tokenCodec, Clock clock,
                       EmailCodeService emailCodeService,
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
        this.emailCodeService = emailCodeService;
        // 없는 이메일도 BCrypt 검사를 거쳐 계정 유무에 따른 응답 시간 차이를 줄여요.
        this.dummyPasswordHash = passwordEncoder.encode(tokenCodec.generate());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        // 재설정과 같은 회원 행을 먼저 잠가 이전 비밀번호로 refresh가 뒤늦게 발급되지 않게 해요.
        User user = users.findByEmailForUpdate(request.email()).orElse(null);
        String encodedPassword = user == null ? dummyPasswordHash : user.getPassword();
        boolean matches = passwordEncoder.matches(request.password(), encodedPassword);
        if (user == null || !matches) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        return issueTokens(user);
    }

    /** S0-1b 가입. 인증을 마친 이메일만 가입하고 바로 로그인 토큰을 줘요. */
    @Transactional
    public LoginResponse signUp(SignupRequest request) {
        if (!PasswordPolicy.isValid(request.password())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
        emailCodeService.requireVerified(request.email());
        if (users.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        User user;
        try {
            user = users.saveAndFlush(User.create(request.email(), passwordEncoder.encode(request.password()),
                    request.nickname().strip(), Boolean.TRUE.equals(request.isMarketingAgreed()),
                    LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입하면 email UNIQUE가 막아요.
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, e);
        }
        emailCodeService.removeCodes(request.email());
        return issueTokens(user);
    }

    private LoginResponse issueTokens(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        String rawRefreshToken = tokenCodec.generate();
        refreshTokens.save(RefreshToken.create(user, tokenCodec.hash(rawRefreshToken), now, now.plusDays(refreshTokenDays)));
        return new LoginResponse(user.getUserId(), user.getNickname(),
                jwtProvider.createAccessToken(user.getUserId()), rawRefreshToken);
    }

    /** 로그아웃 (#79). 이미 지웠거나 모르는 토큰이어도 같은 결과라 여러 번 불러도 돼요. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (tokenCodec.isValidFormat(rawRefreshToken)) {
            refreshTokens.deleteByToken(tokenCodec.hash(rawRefreshToken));
        }
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        if (!tokenCodec.isValidFormat(rawRefreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String digest = tokenCodec.hash(rawRefreshToken);
        Long userId = refreshTokens.userIdByToken(digest)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        // 회원→토큰 순서로 잠가 재설정의 전체 refresh 폐기와 교착되지 않게 해요.
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
