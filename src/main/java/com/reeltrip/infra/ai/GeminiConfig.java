package com.reeltrip.infra.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LlmClient 빈. GEMINI_API_KEY가 있으면 Gemini, 비어 있으면 정해진 카드 2장을 주는 FakeLlmClient를 써요.
 * 키 없이도 BE 1·FE가 로컬에서 링크 분석 흐름(S1-2 → S2-1)을 돌려 볼 수 있게 하려고요.
 */
@Configuration
public class GeminiConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiConfig.class);

    @Bean
    public LlmClient llmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model}") String model,
            @Value("${app.gemini.timeout-seconds}") long timeoutSeconds
    ) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("GEMINI_API_KEY가 비어 있어 가짜 LlmClient(정해진 카드 2장)를 써요.");
            return new FakeLlmClient();
        }
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return new GeminiLlmClient(restClientBuilder.requestFactory(requestFactory), apiKey, model, objectMapper,
                ExtractionPrompt.load());
    }
}
