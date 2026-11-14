package com.reeltripmock;

import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** TODO: 가짜 응답. 각 BE1 기능 이슈에서 실제 서버 구현으로 교체해요. */
@RestController
@Validated
class MockApiController {

    private final Clock clock;
    private final MockSourceFixtures sources;

    MockApiController(Clock clock, MockSourceFixtures sources) {
        this.clock = clock;
        this.sources = sources;
    }

    @PostMapping("/auth/email-codes")
    Map<String, String> sendCode(@Valid @RequestBody EmailRequest request) {
        return Map.of("expiresAt", now().plusMinutes(5).toString(),
                "resendAvailableAt", now().plusSeconds(60).toString());
    }

    @PostMapping("/auth/email-codes/verify")
    Map<String, String> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        if (!"123456".equals(request.code())) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_CODE);
        }
        return Map.of("email", request.email(), "signupAvailableUntil", now().plusMinutes(30).toString());
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> signup(@Valid @RequestBody SignupRequest request) {
        requirePassword(request.password());
        if (!request.isTermsAgreed() || !request.isPrivacyAgreed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return tokens(request.nickname());
    }

    @PostMapping("/auth/login")
    Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        if (!"Mockpass123".equals(request.password())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
        return tokens("테스트");
    }

    @PostMapping("/auth/refresh")
    Map<String, String> refresh(@Valid @RequestBody RefreshRequest request) {
        if (!MockSecurityConfig.REFRESH_TOKEN.equals(request.refreshToken())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return Map.of("accessToken", MockSecurityConfig.ACCESS_TOKEN,
                "refreshToken", MockSecurityConfig.REFRESH_TOKEN);
    }

    @PostMapping("/auth/password-reset")
    void sendReset(@Valid @RequestBody EmailRequest request) {
        // 가짜 서버는 가입 여부와 관계없이 메일 없이 같은 200을 반환해요.
    }

    @PostMapping("/auth/password-reset/verify")
    Map<String, String> verifyReset(@Valid @RequestBody ResetTokenRequest request) {
        requireResetToken(request.token());
        return Map.of("maskedEmail", "de***@example.test", "expiresAt", now().plusMinutes(30).toString());
    }

    @PostMapping("/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmReset(@Valid @RequestBody ResetConfirmRequest request) {
        requireResetToken(request.token());
        requirePassword(request.newPassword());
    }

    @GetMapping("/users/me")
    Map<String, Object> me() {
        return Map.of("userId", 1001, "email", "demo@example.test", "nickname", "테스트",
                "placeCount", 2, "planCount", 1);
    }

    @GetMapping("/sources")
    Map<String, Object> recent(@RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Map.of("sources", sources.recent().stream().skip((long) page * size).limit(size).toList());
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Object> createSource(@Valid @RequestBody SourceRequest request,
            @RequestHeader(name = "X-Mock-Scenario", defaultValue = "success") String scenario) {
        for (String url : request.urls()) {
            try {
                URI uri = URI.create(url);
                if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException error) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        return sources.create(request.urls(), scenario);
    }

    @GetMapping("/sources/{sourceId}")
    Map<String, Object> source(@PathVariable long sourceId) {
        return sources.get(sourceId, imageUrl());
    }

    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> image(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getContentType() == null
                || !List.of("image/png", "image/jpeg", "image/webp").contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return Map.of("imageUrl", imageUrl());
    }

    @GetMapping("/place-search")
    Map<String, Object> search(@RequestParam @NotBlank @Size(max = 200) String query,
                              @RequestHeader(name = "X-Mock-Scenario", defaultValue = "success") String scenario) {
        if ("empty".equals(scenario)) {
            return Map.of("places", List.of());
        }
        return Map.of("places", List.of(Map.of("googlePlaceId", "mock-place-1", "name", "福岡PARCO",
                "address", "福岡市中央区天神2-11-1", "latitude", 33.5914, "longitude", 130.3989,
                "imageUrl", imageUrl())));
    }

    @GetMapping("/routes")
    Map<String, Object> route(@RequestParam @Min(1) long from, @RequestParam @Min(1) long to,
                              @RequestParam(required = false) OffsetDateTime departAt) {
        OffsetDateTime departure = departAt == null ? now() : departAt;
        return Map.of("totalMinutes", 18, "departAt", departure.toString(),
                "arriveAt", departure.plusMinutes(18).toString(), "segments", List.of(
                        segment("WALK", 4, "", ""),
                        segment("SUBWAY", 12, "미도스지선", "난바역"),
                        segment("WALK", 2, "", "")));
    }

    private Map<String, Object> segment(String mode, int minutes, String line, String stop) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("mode", mode);
        fields.put("minutes", minutes);
        fields.put("lineName", line.isEmpty() ? null : line);
        fields.put("stopName", stop.isEmpty() ? null : stop);
        return fields;
    }

    private Map<String, Object> tokens(String nickname) {
        return Map.of("userId", 1001, "nickname", nickname, "accessToken", MockSecurityConfig.ACCESS_TOKEN,
                "refreshToken", MockSecurityConfig.REFRESH_TOKEN);
    }

    private void requirePassword(String password) {
        if (password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    private void requireResetToken(String token) {
        if (!"mock-reset-token".equals(token)) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private String imageUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/mock/place.svg").toUriString();
    }

    record EmailRequest(@NotBlank @Email String email) { }
    record VerifyCodeRequest(@NotBlank @Email String email, @NotBlank String code) { }
    record SignupRequest(@NotBlank @Email String email, @NotBlank String password,
                         @NotBlank @Size(max = 8) String nickname,
                         @NotNull Boolean isTermsAgreed, @NotNull Boolean isPrivacyAgreed,
                         boolean isMarketingAgreed) { }
    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    record RefreshRequest(@NotBlank String refreshToken) { }
    record ResetTokenRequest(@NotBlank String token) { }
    record ResetConfirmRequest(@NotBlank String token, @NotBlank String newPassword) { }
    record SourceRequest(@NotEmpty @Size(max = 5) List<@NotBlank String> urls) { }
}

