package com.reeltrip.domain.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

/** 마이페이지 1:1 문의 접수 (#79). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InquiryApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;

    User member;
    User other;

    @BeforeEach
    void setUp() {
        member = users.save(TestFixtures.user("inquiry-api@example.test"));
        other = users.save(TestFixtures.user("inquiry-other@example.test"));
    }

    @Test
    @DisplayName("문의를 접수하면 201과 문의 ID·접수 시각을 돌려주고, 토큰의 회원 이름으로 유형·내용·알림 희망을 저장한다.")
    void submitStoresInquiryForLoginMember() throws Exception {
        // given / when: 본문에 다른 회원 ID를 넣어도
        String body = submit(Map.of("type", "TRAVEL_CARD_ERROR", "content", "  카드 사진이 안 보여요.  ",
                "wantsReplyNotification", true, "userId", other.getUserId()))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        // then: 로그인한 회원 것으로 저장된다
        JsonNode response = mapper.readTree(body);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.get("createdAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00");
        Map<String, Object> row = jdbc.queryForMap(
                "select user_id, type, content, wants_reply_notification from inquiry where inquiry_id = ?",
                response.get("inquiryId").asLong());
        assertThat(((Number) row.get("USER_ID")).longValue()).isEqualTo(member.getUserId());
        assertThat(row.get("TYPE")).isEqualTo("TRAVEL_CARD_ERROR");
        assertThat(row.get("CONTENT")).isEqualTo("카드 사진이 안 보여요.");
        assertThat(row.get("WANTS_REPLY_NOTIFICATION")).isEqualTo(true);
    }

    @Test
    @DisplayName("내용이 정확히 300자여도 접수한다.")
    void maxLengthContentAccepted() throws Exception {
        // given / when / then
        submit(Map.of("type", "OTHER", "content", "가".repeat(300), "wantsReplyNotification", false))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"REFUND\",\"content\":\"문의\",\"wantsReplyNotification\":false}",
            "{\"content\":\"문의\",\"wantsReplyNotification\":false}",
            "{\"type\":\"OTHER\",\"content\":\"   \",\"wantsReplyNotification\":false}",
            "{\"type\":\"OTHER\",\"wantsReplyNotification\":false}",
            "{\"type\":\"OTHER\",\"content\":\"문의\"}",
            "{\"type\":\"서비스 이용 문의\",\"content\":\"문의\",\"wantsReplyNotification\":false}"})
    @DisplayName("문의 유형·내용·알림 희망 중 하나라도 비었거나 정한 유형이 아니면 접수하지 않는다.")
    void invalidInquiryRejected(String body) throws Exception {
        // given / when / then
        mvc.perform(post("/inquiries").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(jdbc.queryForObject("select count(*) from inquiry", Long.class)).isZero();
    }

    @Test
    @DisplayName("내용이 300자를 넘으면 접수하지 않는다.")
    void tooLongContentRejected() throws Exception {
        // given / when / then
        submit(Map.of("type", "OTHER", "content", "가".repeat(301), "wantsReplyNotification", false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("로그인하지 않으면 문의를 접수할 수 없다.")
    void unauthenticatedRejected() throws Exception {
        // given / when / then
        mvc.perform(post("/inquiries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"content\":\"문의\",\"wantsReplyNotification\":false}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions submit(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/inquiries").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private String bearer() {
        return "Bearer " + jwt.createAccessToken(member.getUserId());
    }
}
