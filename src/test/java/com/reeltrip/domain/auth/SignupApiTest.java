package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.auth.repository.EmailCodeRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.mail.EmailSender;

/** S0-1b 인증번호 받기 → 확인 → 가입. 실제 메일 대신 가짜 발송기로 받은 번호를 써요. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SignupApiTest {

    private static final Instant START = Instant.parse("2026-11-15T01:00:00Z");
    private static final String PASSWORD = "Wayvi1234";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired EmailCodeRepository emailCodes;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtProvider jwt;
    @MockitoBean Clock clock;
    @MockitoBean EmailSender emailSender;

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private String email;

    @BeforeEach
    void setUp() {
        now.set(START);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        email = UUID.randomUUID() + "@example.test";
    }

    private void passTime(Duration duration) {
        now.set(now.get().plus(duration));
    }

    private ResultActions postJson(String path, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private ResultActions sendCode(String target) throws Exception {
        return postJson("/auth/email-codes", Map.of("email", target));
    }

    /** 가장 최근에 메일로 보낸 번호 */
    private String lastSentCode(int expectedSends) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(expectedSends)).sendSignupCode(eq(email), code.capture(), any());
        return code.getValue();
    }

    private ResultActions verifyCode(String code) throws Exception {
        return postJson("/auth/email-codes/verify", Map.of("email", email, "code", code));
    }

    private Map<String, Object> signupBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("nickname", "보영");
        body.put("isTermsAgreed", true);
        body.put("isPrivacyAgreed", true);
        body.put("isMarketingAgreed", false);
        return body;
    }

    private String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    @DisplayName("인증번호를 받으면 6자리 번호를 메일로 보내고 5분 뒤 만료·60초 뒤 재발송 시각을 알려 준다.")
    void sendCode_sendsSixDigitCode() throws Exception {
        // when
        sendCode(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value("2026-11-15T10:05:00+09:00"))
                .andExpect(jsonPath("$.resendAvailableAt").value("2026-11-15T10:01:00+09:00"));
        // then
        assertThat(lastSentCode(1)).matches("[0-9]{6}");
        assertThat(emailCodes.findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc(email)).isPresent();
    }

    @Test
    @DisplayName("이미 가입된 이메일은 인증번호를 보내지 않고 중복 이메일로 실패한다.")
    void sendCode_duplicateEmail() throws Exception {
        // given
        users.save(User.create(email, encoder.encode(PASSWORD), "기존", false, java.time.LocalDateTime.now(clock)));
        // when / then
        sendCode(email).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        verify(emailSender, never()).sendSignupCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("60초 안에 다시 받으면 실패하고, 60초 뒤 새로 받으면 이전 번호는 쓸 수 없다.")
    void sendCode_resendRules() throws Exception {
        // given
        sendCode(email).andExpect(status().isOk());
        String first = lastSentCode(1);
        // when: 59초 뒤
        passTime(Duration.ofSeconds(59));
        sendCode(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_TOO_FREQUENT"));
        // when: 60초 뒤
        passTime(Duration.ofSeconds(1));
        sendCode(email).andExpect(status().isOk());
        String second = lastSentCode(2);
        // then: 이전 번호가 새 번호와 다르면 이전 번호는 틀린 번호
        if (!first.equals(second)) {
            verifyCode(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EMAIL_CODE"));
        }
        verifyCode(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("메일 발송에 실패하면 MAIL_SEND_FAILED로 실패하고 번호는 저장하지 않는다.")
    void sendCode_mailFailure() throws Exception {
        // given
        doThrow(new BusinessException(ErrorCode.MAIL_SEND_FAILED))
                .when(emailSender).sendSignupCode(anyString(), anyString(), any());
        // when / then
        sendCode(email).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MAIL_SEND_FAILED"));
        assertThat(emailCodes.findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc(email)).isEmpty();
    }

    @Test
    @DisplayName("이메일 형식이 아니면 입력 오류로 실패한다.")
    void sendCode_invalidEmail() throws Exception {
        postJson("/auth/email-codes", Map.of("email", "not-an-email"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("번호가 맞으면 확인되고 30분 뒤까지 가입할 수 있다고 알려 준다.")
    void verify_success() throws Exception {
        // given
        sendCode(email);
        // when / then
        verifyCode(lastSentCode(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.signupAvailableUntil").value("2026-11-15T10:30:00+09:00"));
    }

    @Test
    @DisplayName("한 번호를 5번 틀리면 그 번호는 맞게 넣어도 다시 받아야 한다.")
    void verify_fiveWrongAttempts() throws Exception {
        // given
        sendCode(email);
        String code = lastSentCode(1);
        // when: 5번 틀림
        for (int i = 0; i < 5; i++) {
            verifyCode(wrongCode(code)).andExpect(jsonPath("$.code").value("INVALID_EMAIL_CODE"));
        }
        // then
        verifyCode(code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_ATTEMPTS_EXCEEDED"));
    }

    @Test
    @DisplayName("5분이 지난 번호는 만료로 실패한다.")
    void verify_expired() throws Exception {
        // given
        sendCode(email);
        String code = lastSentCode(1);
        passTime(Duration.ofMinutes(5));
        // when / then
        verifyCode(code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("번호를 받은 적 없는 이메일은 틀린 번호로 실패한다.")
    void verify_noCode() throws Exception {
        verifyCode("123456").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EMAIL_CODE"));
    }

    @Test
    @DisplayName("인증을 마치고 가입하면 회원을 만들고 바로 로그인 토큰을 주며, 인증번호 기록은 지운다.")
    void signup_success(CapturedOutput output) throws Exception {
        // given
        sendCode(email);
        String code = lastSentCode(1);
        verifyCode(code).andExpect(status().isOk());
        // when
        String body = postJson("/auth/signup", signupBody()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // then
        JsonNode response = mapper.readTree(body);
        User saved = users.findByEmail(email).orElseThrow();
        assertThat(response.get("userId").asLong()).isEqualTo(saved.getUserId());
        assertThat(response.get("nickname").asText()).isEqualTo("보영");
        assertThat(jwt.parseUserId(response.get("accessToken").asText())).contains(saved.getUserId());
        assertThat(response.get("refreshToken").asText()).isNotBlank();
        assertThat(encoder.matches(PASSWORD, saved.getPassword())).isTrue();
        assertThat(saved.getPassword()).doesNotContain(PASSWORD);
        assertThat(emailCodes.findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc(email)).isEmpty();
        // 가입한 비밀번호로 로그인된다
        postJson("/auth/login", Map.of("email", email, "password", PASSWORD)).andExpect(status().isOk());
        // 인증번호·비밀번호는 로그에 남기지 않는다
        assertThat(output.getAll()).doesNotContain(code).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("인증하지 않은 이메일은 가입할 수 없다.")
    void signup_notVerified() throws Exception {
        // given: 번호만 받고 확인은 안 함
        sendCode(email);
        // when / then
        postJson("/auth/signup", signupBody()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("인증번호를 받은 지 30분이 지나면 확인했어도 가입할 수 없다.")
    void signup_after30Minutes() throws Exception {
        // given
        sendCode(email);
        verifyCode(lastSentCode(1)).andExpect(status().isOk());
        passTime(Duration.ofMinutes(30));
        // when / then
        postJson("/auth/signup", signupBody()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("비밀번호가 영문·숫자 포함 8자 이상이 아니면 비밀번호 형식 오류로 실패한다.")
    void signup_weakPassword() throws Exception {
        // given
        sendCode(email);
        verifyCode(lastSentCode(1));
        for (String weak : new String[] {"abcdefgh", "12345678", "abc123"}) {
            Map<String, Object> body = signupBody();
            body.put("password", weak);
            // when / then
            postJson("/auth/signup", body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_FORMAT"));
        }
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않았거나 닉네임이 8자를 넘으면 입력 오류로 실패한다.")
    void signup_invalidInput() throws Exception {
        // given
        sendCode(email);
        verifyCode(lastSentCode(1));
        Map<String, Object> noTerms = signupBody();
        noTerms.put("isPrivacyAgreed", false);
        Map<String, Object> longNickname = signupBody();
        longNickname.put("nickname", "아주긴닉네임입니다");
        // when / then
        postJson("/auth/signup", noTerms).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        postJson("/auth/signup", longNickname).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("인증한 사이 같은 이메일로 가입된 회원이 있으면 중복 이메일로 실패한다.")
    void signup_duplicateAfterVerify() throws Exception {
        // given
        sendCode(email);
        verifyCode(lastSentCode(1));
        users.save(User.create(email, encoder.encode(PASSWORD), "먼저", false, java.time.LocalDateTime.now(clock)));
        // when / then
        postJson("/auth/signup", signupBody()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }
}
