package com.reeltrip.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Test
    @DisplayName("헬스 체크는 로그인 없이 열린다.")
    void health_withoutLogin_ok() throws Exception {
        // given / when / then
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않고 보호된 주소를 부르면 401 UNAUTHORIZED를 같은 실패 모양으로 받는다.")
    void protectedPath_withoutLogin_unauthorized() throws Exception {
        // given: 토큰 없음
        // when: 로그인이 필요한 주소를 부르면
        // then: 401과 { code, message }
        mockMvc.perform(get("/places"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("잘못된 토큰을 보내면 열린 주소라도 401을 받는다.")
    void invalidToken_unauthorized() throws Exception {
        // given: 서명이 틀린 토큰
        String token = "not-a-jwt";
        // when / then
        mockMvc.perform(get("/plans/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("JwtProvider로 만든 토큰이면 인증을 통과하고, 없는 주소는 404 RESOURCE_NOT_FOUND를 받는다.")
    void validToken_passesAuthentication() throws Exception {
        // given: 회원 1의 토큰
        String token = jwtProvider.createAccessToken(1L);
        // when: 아직 컨트롤러가 없는 주소를 부르면
        // then: 인증은 통과하고 404
        mockMvc.perform(get("/no-such-path").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("JwtProvider는 만든 토큰에서 같은 회원 id를 꺼낸다.")
    void jwtProvider_roundTrip() {
        // given
        String token = jwtProvider.createAccessToken(42L);
        // when / then
        org.assertj.core.api.Assertions.assertThat(jwtProvider.parseUserId(token)).contains(42L);
    }
}
