package com.reeltripmock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.global.exception.ErrorCode;

@SpringBootTest(classes = ApiMockApplication.class)
@AutoConfigureMockMvc
class MockApiContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext context;

    static Stream<Arguments> authCases() {
        return Stream.of(
                Arguments.of("/auth/email-codes", "{\"email\":\"demo@example.test\"}", 200, "expiresAt"),
                Arguments.of("/auth/email-codes/verify", "{\"email\":\"demo@example.test\",\"code\":\"123456\"}", 200, "signupAvailableUntil"),
                Arguments.of("/auth/signup", "{\"email\":\"demo@example.test\",\"password\":\"Mockpass123\",\"nickname\":\"테스트\",\"isTermsAgreed\":true,\"isPrivacyAgreed\":true}", 201, "accessToken"),
                Arguments.of("/auth/login", "{\"email\":\"demo@example.test\",\"password\":\"Mockpass123\"}", 200, "accessToken"),
                Arguments.of("/auth/refresh", "{\"refreshToken\":\"mock-refresh-token\"}", 200, "refreshToken"),
                Arguments.of("/auth/password-reset", "{\"email\":\"unknown@example.test\"}", 200, ""),
                Arguments.of("/auth/password-reset/verify", "{\"token\":\"mock-reset-token\"}", 200, "maskedEmail"),
                Arguments.of("/auth/password-reset/confirm", "{\"token\":\"mock-reset-token\",\"newPassword\":\"Newpass123\"}", 204, ""));
    }

    @ParameterizedTest
    @MethodSource("authCases")
    @DisplayName("인증 API 여덟 개가 약속한 상태 코드와 필드를 반환한다.")
    void authContracts(String path, String body, int expectedStatus, String field) throws Exception {
        // given: 가짜 서버의 공개 인증 요청
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        // when
        var result = mvc.perform(request).andExpect(status().is(expectedStatus));
        // then
        if (!field.isEmpty()) {
            result.andExpect(jsonPath("$." + field).exists()).andExpect(jsonPath("$.data").doesNotExist());
        } else {
            result.andExpect(content().string(""));
        }
    }

    @Test
    @DisplayName("가짜 서버는 DB와 실제 Gemini 클라이언트를 생성하지 않는다.")
    void noRealInfrastructure() {
        // given / when / then
        assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(com.reeltrip.infra.ai.GeminiLlmClient.class)).isEmpty();
        assertThat(context.getBeansOfType(com.reeltrip.domain.user.repository.UserRepository.class)).isEmpty();
    }

    @Test
    @DisplayName("prod 프로필을 함께 켜면 가짜 서버가 시작되지 않는다.")
    void prodCannotStartMockServer() {
        // given / when / then
        assertThatThrownBy(() -> ApiMockApplication.main(new String[]{
                "--spring.profiles.active=prod", "--spring.main.web-application-type=none"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("가짜 API 서버는 prod 프로필로 실행할 수 없어요.");
    }

    @Test
    @DisplayName("내 정보와 최근 수집 내역은 인증한 경우에만 가짜 데이터를 반환한다.")
    void meAndRecent() throws Exception {
        // given: 가짜 로그인 토큰
        // when / then
        mvc.perform(authorized(get("/users/me"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1001)).andExpect(jsonPath("$.placeCount").value(2))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(authorized(get("/sources?page=0&size=1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(1));
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/sources").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("링크 일부가 실패해도 성공한 카드와 원래 링크를 함께 반환한다.")
    void partialSource() throws Exception {
        // given: 성공 링크와 실패 링크
        String body = "{\"urls\":[\"https://youtu.be/mock-first\",\"https://www.instagram.com/reel/mock-last\"]}";
        String accepted = mvc.perform(authorized(post("/sources")).header("X-Mock-Scenario", "partial")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long sourceId = mapper.readTree(accepted).get("sourceId").asLong();
        // when / then
        mvc.perform(authorized(get("/sources/" + sourceId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.sourceLinks[0].url").value("https://youtu.be/mock-first"))
                .andExpect(jsonPath("$.sourceLinks[0].places[0].sourceLinkId").value(sourceId * 10))
                .andExpect(jsonPath("$.sourceLinks[0].places[0].sourceTimestampSec").value(42))
                .andExpect(jsonPath("$.sourceLinks[0].places[0].priceText").value("¥960~980"))
                .andExpect(jsonPath("$.sourceLinks[0].places[0].placeId").doesNotExist())
                .andExpect(jsonPath("$.sourceLinks[0].places[1].category").value("TRANSPORT"))
                .andExpect(jsonPath("$.sourceLinks[1].status").value("FAILED"))
                .andExpect(jsonPath("$.sourceLinks[1].code").value("SOURCE_FETCH_FAILED"))
                .andExpect(jsonPath("$.sourceLinks[1].places").isEmpty());
    }

    @Test
    @DisplayName("전체 실패와 분석 중 상태를 외부 호출 없이 재현한다.")
    void sourceStates() throws Exception {
        // given / when / then
        mvc.perform(authorized(get("/sources/3"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        String accepted = mvc.perform(authorized(post("/sources")).header("X-Mock-Scenario", "analyzing")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"urls\":[\"https://youtu.be/mock\"]}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        long sourceId = mapper.readTree(accepted).get("sourceId").asLong();
        mvc.perform(authorized(get("/sources/" + sourceId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANALYZING")).andExpect(jsonPath("$.title").isEmpty());
    }

    @Test
    @DisplayName("장소 검색과 사진 업로드는 서버의 가짜 이미지 주소를 반환한다.")
    void searchAndImage() throws Exception {
        // given
        var file = new MockMultipartFile("file", "place.png", "image/png", new byte[]{1, 2, 3});
        // when / then
        mvc.perform(authorized(get("/place-search").param("query", "후쿠오카")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.places[0].googlePlaceId").value("mock-place-1"))
                .andExpect(jsonPath("$.places[0].imageUrl").value("http://localhost/mock/place.svg"));
        mvc.perform(authorized(get("/place-search").param("query", "없는 장소")).header("X-Mock-Scenario", "empty"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.places").isEmpty());
        mvc.perform(authorized(multipart("/images").file(file))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("http://localhost/mock/place.svg"));
        mvc.perform(get("/mock/place.svg")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("경로는 요청 출발 시각과 수단별 구간만 반환한다.")
    void routeContract() throws Exception {
        // given
        var request = authorized(get("/routes").param("from", "1").param("to", "2")
                .param("departAt", "2026-11-14T10:00:00+09:00"));
        // when
        JsonNode response = mapper.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        // then
        assertThat(response.get("totalMinutes").asInt()).isEqualTo(18);
        assertThat(response.get("arriveAt").asText()).isEqualTo("2026-11-14T10:18+09:00");
        assertThat(response.get("segments").get(1).get("lineName").asText()).isEqualTo("미도스지선");
        assertThat(response.has("fare")).isFalse();
        assertThat(response.has("transferCount")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"DUPLICATE_EMAIL", "EMAIL_CODE_EXPIRED",
            "EMAIL_CODE_TOO_FREQUENT", "EMAIL_CODE_ATTEMPTS_EXCEEDED", "EMAIL_NOT_VERIFIED",
            "MAIL_SEND_FAILED", "INVALID_RESET_TOKEN", "RESET_TOKEN_EXPIRED", "ROUTE_NOT_FOUND"})
    @DisplayName("실패 화면은 기존 에러 코드와 메시지 형식으로 재현한다.")
    void errorFixtures(ErrorCode code) throws Exception {
        // given: 가짜 서버에서만 읽는 실패 선택 헤더
        // when / then
        mvc.perform(authorized(get("/users/me")).header("X-Mock-Error", code.name()))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message").value(code.getMessage()));
    }

    @Test
    @DisplayName("잘못된 URL과 필수 값 누락을 400으로 반환한다.")
    void invalidRequests() throws Exception {
        // given / when / then
        mvc.perform(authorized(post("/sources")).contentType(MediaType.APPLICATION_JSON).content("{\"urls\":[\"invalid\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(authorized(post("/sources")).contentType(MediaType.APPLICATION_JSON).content("{\"urls\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(authorized(multipart("/images"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@example.test\",\"password\":\"Mockpass123\",\"nickname\":\"테스트\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer mock-access-token");
    }
}
