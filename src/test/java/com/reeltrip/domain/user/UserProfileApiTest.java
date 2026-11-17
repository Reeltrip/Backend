package com.reeltrip.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

/** 마이페이지 프로필 수정 (#79). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProfileApiTest {

    private static final String AVATAR = "https://storage.googleapis.com/wayvi-images/users/1/avatar.png";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;

    User member;

    @BeforeEach
    void setUp() {
        member = users.save(TestFixtures.user("profile-api@example.test"));
    }

    @Test
    @DisplayName("닉네임만 보내면 닉네임만 바뀌고, 응답과 이후 조회 모두 회원 조회와 같은 여섯 필드로 반영된다.")
    void nicknameOnlyChangesNickname() throws Exception {
        // given: 사진이 있는 회원
        modify("{\"avatarUrl\":\"" + AVATAR + "\"}").andExpect(status().isOk());
        // when: 닉네임만 바꾸면
        String body = modify("{\"nickname\":\"  보영  \"}")
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(member.getUserId()))
                .andExpect(jsonPath("$.email").value(member.getEmail()))
                .andExpect(jsonPath("$.nickname").value("보영"))
                .andExpect(jsonPath("$.placeCount").value(0)).andExpect(jsonPath("$.planCount").value(0))
                .andExpect(jsonPath("$.avatarUrl").value(AVATAR))
                .andReturn().getResponse().getContentAsString();
        // then: 사진은 그대로이고 다시 조회해도 같다
        assertThat(mapper.readTree(body).size()).isEqualTo(6);
        mvc.perform(get("/users/me").header("Authorization", bearer()))
                .andExpect(jsonPath("$.nickname").value("보영")).andExpect(jsonPath("$.avatarUrl").value(AVATAR));
    }

    @Test
    @DisplayName("사진 주소를 null로 보내면 기본 사진으로 돌아가고 닉네임은 그대로다.")
    void nullAvatarResetsToDefault() throws Exception {
        // given
        modify("{\"avatarUrl\":\"" + AVATAR + "\"}").andExpect(status().isOk());
        // when / then
        modify("{\"avatarUrl\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value((Object) null))
                .andExpect(jsonPath("$.nickname").value(member.getNickname()));
    }

    @Test
    @DisplayName("이메일이나 다른 회원 ID를 보내도 로그인한 회원의 닉네임·사진만 바뀐다.")
    void cannotChangeEmailOrOtherMember() throws Exception {
        // given
        User other = users.save(TestFixtures.user("profile-other@example.test"));
        // when
        modify("{\"nickname\":\"새이름\",\"email\":\"hacked@example.test\",\"userId\":" + other.getUserId() + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(member.getUserId()))
                .andExpect(jsonPath("$.email").value(member.getEmail()));
        // then
        users.flush();
        assertThat(users.findById(other.getUserId()).orElseThrow().getNickname()).isEqualTo("여행자");
        assertThat(users.findByEmail("hacked@example.test")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nickname\":\"   \"}", "{\"nickname\":null}", "{\"nickname\":\"아홉글자닉네임이다\"}"})
    @DisplayName("닉네임은 비우거나 9자 이상으로 바꿀 수 없다.")
    void invalidNicknameRejected(String body) throws Exception {
        // given / when / then
        modify(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(users.findById(member.getUserId()).orElseThrow().getNickname()).isEqualTo("여행자");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/a.png", "javascript:alert(1)", "/users/1/a.png", "",
            "https://user:pw@example.com/a.png"})
    @DisplayName("사진 주소는 https 이미지 주소만 받는다.")
    void invalidAvatarRejected(String avatarUrl) throws Exception {
        // given / when / then
        modify(mapper.writeValueAsString(java.util.Map.of("avatarUrl", avatarUrl)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("사진 주소가 2048자를 넘으면 저장하지 않는다.")
    void tooLongAvatarRejected() throws Exception {
        // given
        String longUrl = "https://storage.googleapis.com/" + "a".repeat(2048);
        // when / then
        modify("{\"avatarUrl\":\"" + longUrl + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("로그인하지 않으면 프로필을 바꿀 수 없다.")
    void unauthenticatedRejected() throws Exception {
        // given / when / then
        mvc.perform(patch("/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"보영\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions modify(String body) throws Exception {
        return mvc.perform(patch("/users/me").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer() {
        return "Bearer " + jwt.createAccessToken(member.getUserId());
    }
}
