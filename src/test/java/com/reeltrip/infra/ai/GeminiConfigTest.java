package com.reeltrip.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.fetcher.FetchedContent;

class GeminiConfigTest {

    private final GeminiConfig config = new GeminiConfig();
    private final MockEnvironment localEnvironment = new MockEnvironment();

    @Test
    @DisplayName("GEMINI_API_KEY가 비어 있으면 정해진 카드 2장을 주는 가짜 LlmClient를 쓴다.")
    void llmClient_blankKey_usesFake() {
        // given / when
        LlmClient client = config.llmClient(RestClient.builder(), new ObjectMapper(), localEnvironment, " ", "gemini-test", 40);

        // then
        assertThat(client).isInstanceOf(FakeLlmClient.class);
        ExtractionResult result = client.extractCards(
                new FetchedContent(Platform.YOUTUBE, "https://youtu.be/abcDEF12345", null, null));
        assertThat(result.cards()).hasSize(2);
        assertThat(result.cards()).allSatisfy(card -> assertThat(card.sourceUrl()).isEqualTo("https://youtu.be/abcDEF12345"));
    }

    @Test
    @DisplayName("GEMINI_API_KEY가 있으면 Gemini LlmClient를 쓴다.")
    void llmClient_withKey_usesGemini() {
        // given / when
        LlmClient client = config.llmClient(RestClient.builder(), new ObjectMapper(), localEnvironment, "test-key", "gemini-test", 40);

        // then
        assertThat(client).isInstanceOf(GeminiLlmClient.class);
    }

    @Test
    @DisplayName("prod 프로필에서 키가 비면 가짜 카드가 아니라 AI_EXTRACTION_FAILED로 실패한다.")
    void llmClient_prodBlankKey_failsInsteadOfFake() {
        // given: prod 프로필, 빈 키
        MockEnvironment prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");

        // when
        LlmClient client = config.llmClient(RestClient.builder(), new ObjectMapper(), prodEnvironment, "", "gemini-test", 40);

        // then
        assertThat(client).isNotInstanceOf(FakeLlmClient.class);
        assertThatThrownBy(() -> client.extractCards(
                new FetchedContent(Platform.YOUTUBE, "https://youtu.be/abcDEF12345", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_EXTRACTION_FAILED);
    }
}
