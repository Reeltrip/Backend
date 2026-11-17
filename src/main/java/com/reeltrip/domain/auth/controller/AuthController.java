package com.reeltrip.domain.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.auth.dto.EmailCodeSendRequest;
import com.reeltrip.domain.auth.dto.EmailCodeSendResponse;
import com.reeltrip.domain.auth.dto.EmailCodeVerifyRequest;
import com.reeltrip.domain.auth.dto.EmailCodeVerifyResponse;
import com.reeltrip.domain.auth.dto.LoginRequest;
import com.reeltrip.domain.auth.dto.LoginResponse;
import com.reeltrip.domain.auth.dto.RefreshRequest;
import com.reeltrip.domain.auth.dto.SignupRequest;
import com.reeltrip.domain.auth.dto.TokenResponse;
import com.reeltrip.domain.auth.service.AuthService;
import com.reeltrip.domain.auth.service.EmailCodeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    public AuthController(AuthService authService, EmailCodeService emailCodeService) {
        this.authService = authService;
        this.emailCodeService = emailCodeService;
    }

    /** S0-1b 인증번호 받기 */
    @PostMapping("/email-codes")
    public EmailCodeSendResponse sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request) {
        return emailCodeService.sendCode(request.email());
    }

    /** S0-1b 인증번호 확인 */
    @PostMapping("/email-codes/verify")
    public EmailCodeVerifyResponse verifyEmailCode(@Valid @RequestBody EmailCodeVerifyRequest request) {
        return emailCodeService.verifyCode(request.email(), request.code());
    }

    /** S0-1b 가입 후 바로 로그인 */
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signUp(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .body(authService.signUp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .body(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .body(authService.refresh(request.refreshToken()));
    }

    /** 마이페이지 로그아웃 (#79). 다른 기기의 로그인은 그대로 둬요. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
