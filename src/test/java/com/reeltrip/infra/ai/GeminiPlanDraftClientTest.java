package com.reeltrip.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** 실제 Gemini 대신 가짜 서버로 계획 초안 호출의 재시도·실패를 확인해요. */
class GeminiPlanDraftClientTest {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent";

    private MockRestServiceServer server;
    private GeminiPlanDraftClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PlanDraftRequest request = new PlanDraftRequest("오사카", 1, List.of(
            new PlanDraftRequest.Candidate(1L, Category.FOOD, "이치란", 34.66, 135.50, List.of("라멘")),
            new PlanDraftRequest.Candidate(10L, Category.STAY, "호텔", 34.67, 135.49, List.of())));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiPlanDraftClient(builder, "test-key", "gemini-test", objectMapper);
    }

    /** Gemini generateContent 응답 모양으로 text를 감싸요 */
    private String geminiResponse(String text) throws Exception {
        var root = objectMapper.createObjectNode();
        root.putArray("candidates").addObject().putObject("content").putArray("parts").addObject().put("text", text);
        return objectMapper.writeValueAsString(root);
    }

    @Test
    @DisplayName("후보 카드의 id·좌표를 넘기고, 키는 헤더로만 보내며, 받은 초안을 돌려준다.")
    void draftPlan_normal() throws Exception {
        // given
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"placeId\\\":1")))
                .andRespond(withSuccess(geminiResponse(
                        "{\"stayPlaceIds\":[10],\"days\":[{\"dayNumber\":1,\"placeIds\":[1]}]}"),
                        MediaType.APPLICATION_JSON));
        // when
        PlanDraftResult result = client.draftPlan(request);
        // then
        server.verify();
        assertThat(result.stayPlaceIds()).containsExactly(10L);
        assertThat(result.days()).containsExactly(List.of(1L));
    }

    @Test
    @DisplayName("형식이 깨진 응답이 오면 한 번만 다시 불러 성공한 초안을 돌려준다.")
    void draftPlan_retryOnceOnMalformed() throws Exception {
        // given: 첫 응답은 JSON이 아님, 두 번째는 정상
        server.expect(once(), requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiResponse("미안해요"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiResponse("{\"days\":[{\"dayNumber\":1,\"placeIds\":[1]}]}"),
                        MediaType.APPLICATION_JSON));
        // when
        PlanDraftResult result = client.draftPlan(request);
        // then
        server.verify();
        assertThat(result.days()).containsExactly(List.of(1L));
    }

    @Test
    @DisplayName("형식이 두 번 깨지면 AI 초안 실패로 끝난다.")
    void draftPlan_malformedTwice_fails() throws Exception {
        // given
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiResponse("미안해요"), MediaType.APPLICATION_JSON));
        // when / then
        assertThatThrownBy(() -> client.draftPlan(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_DRAFT_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("Gemini가 5xx로 실패하면 다시 부르지 않고 AI 초안 실패로 끝난다.")
    void draftPlan_serverError_fails() {
        // given
        server.expect(once(), requestTo(ENDPOINT)).andRespond(withServerError());
        // when / then
        assertThatThrownBy(() -> client.draftPlan(request))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_DRAFT_FAILED);
        server.verify();
    }
}
