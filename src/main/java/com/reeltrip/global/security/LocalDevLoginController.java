// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.global.security;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로컬(local 프로필)에서만 열리는 테스트 로그인. 회원가입·로그인 API(BE 1)가 생기기 전에
 * 로컬에서 BE 2 API를 직접 불러 보려고 둬요. prod에는 이 빈이 아예 없어요.
 * 실제 로그인 구현 후에는 명시적으로 켠 local 환경에서만 유지해요.
 */
@Profile("local & !prod")
@ConditionalOnProperty(name = "app.auth.dev-login.enabled", havingValue = "true")
@RestController
public class LocalDevLoginController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    public LocalDevLoginController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                   JwtProvider jwtProvider, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.clock = clock;
    }

    /** 이메일로 회원을 찾거나 만들고 액세스 토큰을 줘요. */
    @PostMapping("/auth/dev-login")
    @Transactional
    public DevLoginResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> userRepository.save(User.create(request.email(),
                        passwordEncoder.encode(UUID.randomUUID().toString()), request.nickname(), false,
                        LocalDateTime.now(clock))));
        return new DevLoginResponse(user.getUserId(), user.getNickname(), jwtProvider.createAccessToken(user.getUserId()));
    }

    public record DevLoginRequest(@NotBlank @Email String email, @NotBlank String nickname) {
    }

    public record DevLoginResponse(Long userId, String nickname, String accessToken) {
    }
}
