package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.auth.dto.*;
import com.reeltrip.domain.auth.entity.PasswordResetToken;
import com.reeltrip.domain.auth.repository.PasswordResetTokenRepository;
import com.reeltrip.domain.auth.service.*;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.*;
import com.reeltrip.infra.mail.EmailSender;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetApiTest {
    private static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");
    private static final String OLD = "ResetOld123";
    private static final String NEXT = "ResetNext456";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired PasswordResetService service;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    @MockitoBean EmailSender mail;
    private final Map<String,String> delivered = new ConcurrentHashMap<>();
    private User user;

    @BeforeEach void prepare() {
        time(NOW);
        user = account();
        doAnswer(call -> { delivered.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(mail).sendPasswordReset(anyString(), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test @DisplayName("비동기 요청·반복 확인 뒤 비밀번호를 한 번 바꾸고 본인 refresh만 모두 폐기한다.")
    void completeHttpFlow(CapturedOutput output) throws Exception {
        // given
        String first = auth.login(new LoginRequest(user.getEmail(), OLD)).refreshToken();
        String second = auth.login(new LoginRequest(user.getEmail(), OLD)).refreshToken();
        User other = account();
        String unrelated = auth.login(new LoginRequest(other.getEmail(), OLD)).refreshToken();
        // when
        mvc.perform(post("/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", user.getEmail()))))
                .andExpect(status().isOk()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"));
        String raw = awaitCommittedToken(user.getEmail());
        // then
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("select token from password_reset_token where user_id=?", String.class, user.getUserId()))
                .isEqualTo(PasswordResetToken.hashToken(raw)).doesNotContain(raw);
        for (int attempt=0; attempt<2; attempt++) {
            mvc.perform(post("/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("token", raw))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.maskedEmail").value(org.hamcrest.Matchers.containsString("*****@")))
                    .andExpect(jsonPath("$.expiresAt").value("2026-11-16T12:30:00+09:00"));
        }
        mvc.perform(post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", raw, "newPassword", NEXT))))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyError(raw, "INVALID_RESET_TOKEN");
        assertThatThrownBy(() -> auth.login(new LoginRequest(user.getEmail(), OLD))).isInstanceOf(BusinessException.class);
        assertThat(auth.login(new LoginRequest(user.getEmail(), NEXT)).accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.refresh(first)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> auth.refresh(second)).isInstanceOf(BusinessException.class);
        assertThat(auth.refresh(unrelated).refreshToken()).isNotBlank();
        assertThat(output.getAll()).doesNotContain(raw, OLD, NEXT);
    }

    @Test @DisplayName("가입하지 않은 주소도 빈200이고 형식 오류만400으로 반환한다.")
    void unknownAndMalformedEmail() throws Exception {
        // given / when / then
        mvc.perform(post("/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+UUID.randomUUID()+"@example.test\"}"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(post("/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test @DisplayName("60초 안에는 추가 발송하지 않고 경계 뒤 새 링크만 유효하다.")
    void cooldownAndReplacement() throws Exception {
        // given
        service.request(user.getEmail()); String original = delivered.get(user.getEmail());
        // when
        time(NOW.plusSeconds(59)); service.request(user.getEmail());
        assertThat(delivered.get(user.getEmail())).isEqualTo(original);
        time(NOW.plusSeconds(60)); service.request(user.getEmail());
        // then
        String next = delivered.get(user.getEmail());
        assertThat(next).isNotEqualTo(original);
        verifyError(original, "INVALID_RESET_TOKEN");
        assertThat(service.verify(next).expiresAt().toInstant()).isEqualTo(NOW.plusSeconds(60+1800));
        verify(mail, times(2)).sendPasswordReset(eq(user.getEmail()), anyString(), any());
    }

    @Test @DisplayName("30분 경계에서 만료되며 확인·변경 실패가 토큰을 소비하지 않는다.")
    void expiryBoundary() throws Exception {
        // given
        service.request(user.getEmail()); String raw=delivered.get(user.getEmail());
        // when / then
        time(NOW.plusSeconds(1799)); assertThat(service.verify(raw)).isNotNull();
        time(NOW.plusSeconds(1800)); verifyError(raw, "RESET_TOKEN_EXPIRED");
        mvc.perform(post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", raw, "newPassword", NEXT))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_TOKEN_EXPIRED"));
        assertThat(resets.findByTokenHash(PasswordResetToken.hashToken(raw)).orElseThrow().isUsed()).isFalse();
    }

    @Test @DisplayName("SMTP 실패는 이전 링크 폐기와 새 토큰 저장을 되돌려 곧바로 재요청할 수 있다.")
    void mailFailureRollsBack() {
        // given
        service.request(user.getEmail()); String original=delivered.get(user.getEmail());
        time(NOW.plusSeconds(61));
        doThrow(new BusinessException(ErrorCode.MAIL_SEND_FAILED)).when(mail).sendPasswordReset(any(), any(), any());
        // when / then
        assertThatThrownBy(() -> service.request(user.getEmail())).isInstanceOf(BusinessException.class);
        assertThat(service.verify(original)).isNotNull();
        doNothing().when(mail).sendPasswordReset(any(), any(), any());
        service.request(user.getEmail());
        assertThat(resets.findByTokenHash(PasswordResetToken.hashToken(original))).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"", "short1", "abcdefgh", "12345678", "암호1234567890123456789012345678901234567890123456789012345678901234567890"})
    @DisplayName("가입과 같은 비밀번호 규칙을 지키지 않으면 변경하지 않고 링크를 유지한다.")
    void invalidPasswordKeepsToken(String value) throws Exception {
        // given
        service.request(user.getEmail()); String raw=delivered.get(user.getEmail());
        // when / then
        mvc.perform(post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", raw, "newPassword", value))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PASSWORD_FORMAT"));
        assertThat(service.verify(raw)).isNotNull();
    }

    @ParameterizedTest @ValueSource(strings={"", "bad-token", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    @DisplayName("형식이 틀린 토큰은 본문 값이나 로그에 노출하지 않고 거부한다.")
    void invalidToken(String raw, CapturedOutput output) throws Exception {
        // given / when / then
        verifyError(raw, "INVALID_RESET_TOKEN");
        if (!raw.isBlank()) { assertThat(output.getAll()).doesNotContain(raw); }
        assertThat(new PasswordResetConfirmRequest(raw, NEXT).toString()).doesNotContain(NEXT);
    }

    private void verifyError(String raw,String code) throws Exception {
        mvc.perform(post("/auth/password-reset/verify").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", raw))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
    }
    private void time(Instant now) { when(clock.instant()).thenReturn(now); when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul")); }
    private User account() { return users.save(User.create(UUID.randomUUID()+"@example.test",encoder.encode(OLD),"재설정",false,LocalDateTime.now(clock))); }
    private String awaitCommittedToken(String email) throws InterruptedException {
        long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();
        do {
            String raw=delivered.get(email);
            if(raw!=null && resets.findByTokenHash(PasswordResetToken.hashToken(raw)).isPresent()) { return raw; }
            Thread.sleep(10);
        } while(System.nanoTime()<end);
        throw new AssertionError("메일 작업이 커밋되지 않았어요.");
    }
}
