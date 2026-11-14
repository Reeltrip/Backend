package com.reeltrip.infra.fetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.ai.ExtractionPrompt;
import com.reeltrip.infra.ai.GeminiLlmClient;

class YoutubeGeminiContractTest {

    @ParameterizedTest
    @CsvSource({"https://www.youtube.com/watch?v=abcDEF12345,https://www.youtube.com/watch?v=abcDEF12345",
            "https://www.youtube.com/shorts/abcDEF12345?si=share,https://www.youtube.com/watch?v=abcDEF12345",
            "https://youtu.be/abcDEF12345?t=42,https://www.youtube.com/watch?v=abcDEF12345"})
    @DisplayName("수집한 일반·쇼츠·단축 영상이 기존 Gemini 입력으로 이어지고 원본·카드 타임스탬프가 보존된다.")
    void feedsExistingGeminiWithoutChangingDraftContract(String original, String geminiUrl) throws Exception {
        // given: 실제 oEmbed·Gemini 대신 고정 응답을 사용해요.
        var metadata = mock(YoutubeOembedClient.class);
        when(metadata.fetch("abcDEF12345")).thenReturn(Optional.empty());
        var urls = new SourceUrlClassifier();
        var fetcher = new SourceContentFetcher(urls, List.of(new YoutubeContentFetcher(urls, metadata)));
        var mapper = new ObjectMapper();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var llm = new GeminiLlmClient(builder, "test-only-key", "test-model", mapper, ExtractionPrompt.load());
        server.expect(jsonPath("$.contents[0].parts[0].file_data.file_uri").value(geminiUrl))
                .andRespond(withSuccess(new ClassPathResource("gemini/normal.json"), MediaType.APPLICATION_JSON));
        // when: 썸네일이 없어도 영상 입력으로 분석할 수 있어요.
        var content = fetcher.fetch(original);
        var result = llm.extractCards(content);
        // then
        server.verify();
        assertThat(content.text()).isNull();
        assertThat(content.thumbnailUrl()).isNull();
        assertThat(result.cards()).hasSize(2);
        assertThat(result.cards().getFirst().sourceUrl()).isEqualTo(original);
        assertThat(result.cards().getFirst().sourceTimestampSec()).isEqualTo(42);
    }
}
