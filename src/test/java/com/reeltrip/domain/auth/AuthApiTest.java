package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.auth.dto.LoginRequest;
import com.reeltrip.domain.auth.dto.LoginResponse;
import com.reeltrip.domain.auth.dto.RefreshRequest;
import com.reeltrip.domain.auth.dto.TokenResponse;
import com.reeltrip.domain.auth.repository.RefreshTokenRepository;
import com.reeltrip.domain.auth.service.AuthService;
import com.reeltrip.domain.auth.service.RefreshTokenCodec;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;

@SpringBootTest(properties = "logging.level.com.reeltrip.global.exception.GlobalExceptionHandler=DEBUG")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthApiTest {

    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String PASSWORD = "LoginPass123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtProvider jwt;
    @Autowired RefreshTokenCodec codec;
    @Autowired AuthService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;

    User user;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(SEOUL);
        user = users.save(User.create(UUID.randomUUID() + "@example.test", encoder.encode(PASSWORD),
                "로그인", false, LocalDateTime.now(clock)));
    }

    @Test
    @DisplayName("BCrypt 비밀번호가 맞으면 회원 정보와 두 토큰을 발급하고 DB에는 해시만 저장한다.")
    void loginIssuesTokensAndStoresOnlyHash() throws Exception {
        // given / when
        JsonNode response = login();
        // then
        assertThat(response.size()).isEqualTo(4);
        assertThat(response.get("userId").asLong()).isEqualTo(user.getUserId());
        assertThat(response.get("nickname").asText()).isEqualTo("로그인");
        String access = response.get("accessToken").asText();
        String refresh = response.get("refreshToken").asText();
        assertThat(jwt.parseUserId(access)).contains(user.getUserId());
        assertThat(refresh).matches("[A-Za-z0-9_-]{43}");
        assertThat(refreshTokens.findByToken(refresh)).isEmpty();
        var stored = refreshTokens.findByToken(codec.hash(refresh)).orElseThrow();
        assertThat(stored.getExpiresAt()).isEqualTo(LocalDateTime.now(clock).plusDays(14));
        assertThat(jdbc.queryForObject("select token from refresh_token where refresh_token_id = ?",
                String.class, stored.getRefreshTokenId())).isEqualTo(codec.hash(refresh)).doesNotContain(refresh);
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 INVALID_LOGIN 응답을 반환한다.")
    void loginDoesNotExposeAccountExistence() throws Exception {
        // given / when
        String missing = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "missing-" + UUID.randomUUID() + "@example.test", "password", PASSWORD))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String wrong = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "WrongPass123"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_LOGIN"))
                .andReturn().getResponse().getContentAsString();
        // then
        assertThat(missing).isEqualTo(wrong);
    }

    @Test
    @DisplayName("재발급하면 이전 토큰은 폐기되고 새 토큰만 사용할 수 있다.")
    void refreshRotatesAtomicallyAndKeepsExpiry() throws Exception {
        // given
        JsonNode login = login();
        String original = login.get("refreshToken").asText();
        var before = refreshTokens.findByToken(codec.hash(original)).orElseThrow();
        // when
        JsonNode next = refresh(original);
        // then
        assertThat(next.size()).isEqualTo(2);
        assertThat(next.get("accessToken").asText()).isNotEqualTo(login.get("accessToken").asText());
        assertThat(next.get("refreshToken").asText()).isNotEqualTo(original);
        assertThat(refreshTokens.findByToken(codec.hash(original))).isEmpty();
        var after = refreshTokens.findByToken(codec.hash(next.get("refreshToken").asText())).orElseThrow();
        assertThat(after.getRefreshTokenId()).isEqualTo(before.getRefreshTokenId());
        assertThat(after.getExpiresAt()).isEqualTo(before.getExpiresAt());
        rejectRefresh(original);
        refresh(next.get("refreshToken").asText());
    }

    @Test
    @DisplayName("리프레시 만료 시각부터 재발급할 수 없다.")
    void expiredRefreshRejected() throws Exception {
        // given
        String token = login().get("refreshToken").asText();
        // when
        when(clock.instant()).thenReturn(NOW.plusSeconds(14L * 24 * 60 * 60));
        // then
        rejectRefresh(token);
    }

    @Test
    @DisplayName("DB에서 폐기한 리프레시 토큰은 재발급할 수 없다.")
    void revokedRefreshRejected() throws Exception {
        // given
        String token = login().get("refreshToken").asText();
        refreshTokens.deleteById(refreshTokens.findByToken(codec.hash(token)).orElseThrow().getRefreshTokenId());
        // when / then
        rejectRefresh(token);
    }

    @Test
    @DisplayName("액세스 JWT나 변조된 리프레시 토큰은 재발급에 사용할 수 없다.")
    void accessAndForgedTokensRejected() throws Exception {
        // given
        JsonNode login = login();
        String refresh = login.get("refreshToken").asText();
        String forged = (refresh.charAt(0) == 'A' ? "B" : "A") + refresh.substring(1);
        // when / then
        rejectRefresh(login.get("accessToken").asText());
        rejectRefresh(forged);
        rejectRefresh("A".repeat(43));
    }

    @Test
    @DisplayName("변조되거나 만료된 액세스 JWT로 보호된 API에 접근할 수 없다.")
    void tamperedAndExpiredAccessRejected() throws Exception {
        // given
        String token = login().get("accessToken").asText();
        int signature = token.lastIndexOf('.') + 1;
        String forged = token.substring(0, signature) + (token.charAt(signature) == 'A' ? "B" : "A") + token.substring(signature + 1);
        // when / then
        mvc.perform(get("/places").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(30 * 60));
        mvc.perform(get("/places").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("실제 로그인 토큰으로 BE2 카드와 계획 API를 만들고 조회할 수 있다.")
    void tokenWorksWithPlaceAndPlanApis() throws Exception {
        // given
        String token = login().get("accessToken").asText();
        String bearer = "Bearer " + token;
        // when / then
        mvc.perform(post("/places").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("""
                {"places":[{"category":"FOOD","name":"테스트 장소","address":"후쿠오카","latitude":33.5,
                "longitude":130.4,"imageUrl":"https://example.test/image.png","city":"후쿠오카"}]}
                """)).andExpect(status().isCreated());
        mvc.perform(post("/plans").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"테스트 여행","startDate":"2026-11-20","endDate":"2026-11-21","city":"후쿠오카"}
                """)).andExpect(status().isCreated());
        mvc.perform(get("/places").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.places.length()").value(1));
        mvc.perform(get("/plans?owner=me").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plans.length()").value(1));
    }

    @Test
    @DisplayName("잘못된 입력은 400으로 반환하고 거부된 비밀번호와 토큰을 로그에 남기지 않는다.")
    void invalidInputsDoNotLeakSecrets(CapturedOutput output) throws Exception {
        // given
        String password = "password-not-for-log-".repeat(20);
        String token = "token-not-for-log-".repeat(40);
        // when
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", password))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // then
        assertThat(output.getAll()).doesNotContain(password).doesNotContain(token);
        assertThat(new LoginRequest(user.getEmail(), PASSWORD).toString()).doesNotContain(PASSWORD);
        assertThat(new RefreshRequest("sensitive-token").toString()).doesNotContain("sensitive-token");
        assertThat(new TokenResponse("access-secret", "refresh-secret").toString()).doesNotContain("access-secret", "refresh-secret");
        assertThat(new LoginResponse(1L, "회원", "access-secret", "refresh-secret").toString())
                .doesNotContain("access-secret", "refresh-secret");
    }

    @Test
    @DisplayName("BCrypt의 72바이트를 넘는 비밀번호는 서버 오류 대신 로그인 실패로 반환한다.")
    void bcryptByteLimitIsHandled() throws Exception {
        // given / when / then
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "가".repeat(25)))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    @DisplayName("동시에 같은 토큰을 재발급하면 한 요청만 성공한다.")
    void concurrentRefreshOnlySucceedsOnce() throws Exception {
        // given
        String refresh = login().get("refreshToken").asText();
        // when / then
        ConcurrentRefreshAssertions.assertOnlyOneSuccess(service, refresh);
    }

    @Test
    @DisplayName("기본 설정에서는 비밀번호 없는 개발용 로그인이 열리지 않는다.")
    void devLoginIsDisabledByDefault() throws Exception {
        // given / when / then
        mvc.perform(post("/auth/dev-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@example.test\",\"nickname\":\"개발\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공개 인증 API에도 잘못된 Authorization을 보내면 기존 정책대로 거부한다.")
    void refreshRequiresOmittingInvalidAccessHeader() throws Exception {
        // given
        String token = login().get("refreshToken").asText();
        // when / then
        mvc.perform(post("/auth/refresh").header("Authorization", "Bearer invalid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        refresh(token);
    }

    private JsonNode login() throws Exception {
        String json = mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD));
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private JsonNode refresh(String token) throws Exception {
        String response = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void rejectRefresh(String token) throws Exception {
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
