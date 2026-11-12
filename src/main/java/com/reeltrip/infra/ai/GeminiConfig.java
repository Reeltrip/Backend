package com.reeltrip.infra.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/**
 * LlmClient 빈. GEMINI_API_KEY가 있으면 Gemini를 써요.
 * 키가 비면 prod가 아닐 때만 정해진 카드 2장을 주는 FakeLlmClient를 써요. 키 없이도 BE 1·FE가 로컬에서
 * 링크 분석 흐름(S1-2 → S2-1)을 돌려 볼 수 있게 하려고요. prod에서는 가짜 카드가 조용히 나가지 않게 매번 실패시켜요.
 */
@Configuration
public class GeminiConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiConfig.class);

    @Bean
    public LlmClient llmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model}") String model,
            @Value("${app.gemini.timeout-seconds}") long timeoutSeconds
    ) {
        if (!StringUtils.hasText(apiKey)) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                // 서버는 띄워 두고(다른 화면은 동작) 링크 분석만 S1-3 실패 화면으로 보내요.
                log.error("GEMINI_API_KEY가 비어 있어요. 링크 분석은 모두 AI_EXTRACTION_FAILED로 실패해요.");
                return content -> {
                    throw new BusinessException(ErrorCode.AI_EXTRACTION_FAILED);
                };
            }
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

    /** S4-2 AI 초안. 키가 없을 때 처리는 llmClient와 같아요(prod는 실패, 그 밖은 가짜). */
    @Bean
    public PlanDraftClient planDraftClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model}") String model,
            @Value("${app.gemini.timeout-seconds}") long timeoutSeconds
    ) {
        if (!StringUtils.hasText(apiKey)) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                log.error("GEMINI_API_KEY가 비어 있어요. AI 초안은 모두 AI_DRAFT_FAILED로 실패해요.");
                return request -> {
                    throw new BusinessException(ErrorCode.AI_DRAFT_FAILED);
                };
            }
            log.warn("GEMINI_API_KEY가 비어 있어 가짜 AI 초안(경도 순으로 나누기)을 써요.");
            return new FakePlanDraftClient();
        }
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return new GeminiPlanDraftClient(restClientBuilder.clone().requestFactory(requestFactory), apiKey, model,
                objectMapper);
    }
}
