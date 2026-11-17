package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.auth.repository.RefreshTokenRepository;
import com.reeltrip.domain.auth.service.RefreshTokenCodec;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;

/** 마이페이지 로그아웃 (#79). 지금 쓰는 리프레시 토큰 하나만 폐기해요. */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutApiTest {

    private static final String PASSWORD = "LogoutPass123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired RefreshTokenCodec codec;
    @Autowired PasswordEncoder encoder;

    User user;

    @BeforeEach
    void setUp() {
        user = users.save(User.create(UUID.randomUUID() + "@example.test", encoder.encode(PASSWORD),
                "로그아웃", false, LocalDateTime.of(2026, 11, 1, 0, 0)));
    }

    @Test
    @DisplayName("로그아웃하면 204를 돌려주고, 그 리프레시 토큰으로는 다시 재발급할 수 없다.")
    void logoutRevokesRefreshToken() throws Exception {
        // given
        String refresh = login();
        // when
        logout(refresh).andExpect(status().isNoContent()).andExpect(content().string(""));
        // then
        assertThat(refreshTokens.findByToken(codec.hash(refresh))).isEmpty();
        refresh(refresh).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("한 기기에서 로그아웃해도 다른 기기의 로그인은 그대로 유지된다.")
    void otherDeviceStaysLoggedIn() throws Exception {
        // given: 같은 회원이 두 기기에서 로그인
        String phone = login();
        String laptop = login();
        // when
        logout(phone).andExpect(status().isNoContent());
        // then
        refresh(laptop).andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    @DisplayName("이미 로그아웃했거나 없는 토큰으로 다시 로그아웃해도 같은 204를 돌려준다.")
    void repeatedLogoutIsSafe() throws Exception {
        // given
        String refresh = login();
        logout(refresh).andExpect(status().isNoContent());
        // when / then
        logout(refresh).andExpect(status().isNoContent());
        logout(codec.generate()).andExpect(status().isNoContent());
        logout("not-a-refresh-token").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("재발급으로 바뀐 새 리프레시 토큰으로 로그아웃하면 그 로그인이 끝난다.")
    void logoutWithRotatedToken() throws Exception {
        // given
        String first = login();
        String rotated = mapper.readTree(refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        // when
        logout(rotated).andExpect(status().isNoContent());
        // then
        refresh(rotated).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":null}"})
    @DisplayName("리프레시 토큰을 보내지 않으면 입력 오류로 응답한다.")
    void missingTokenRejected(String body) throws Exception {
        // given / when / then
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private String login() throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("refreshToken").asText();
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }
}
