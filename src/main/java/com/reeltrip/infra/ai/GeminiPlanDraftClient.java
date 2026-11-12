package com.reeltrip.infra.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/**
 * Gemini로 계획 초안 만들기 (S4-2). 후보 카드의 id·좌표를 넘기고 일자별 순서를 JSON으로 받아요.
 * 형식이 깨진 응답만 한 번 다시 호출하고, 시간 초과·4xx·5xx는 바로 AI_DRAFT_FAILED예요.
 */
public class GeminiPlanDraftClient implements PlanDraftClient {

    private static final String PROMPT_PATH = "prompts/plan-draft.txt";
    private static final int MAX_ATTEMPTS = 2;
    private static final Logger log = LoggerFactory.getLogger(GeminiPlanDraftClient.class);

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;
    private final PlanDraftResponseParser parser;
    private final String systemInstruction;

    /** builder에는 시간 제한이 걸린 요청 팩토리를 미리 넣어서 넘겨요 (GeminiConfig). */
    public GeminiPlanDraftClient(RestClient.Builder builder, String apiKey, String model, ObjectMapper objectMapper) {
        // 키는 주소가 아니라 헤더로 보내서 접속 기록에 남지 않게 해요.
        this.restClient = builder.baseUrl(GeminiLlmClient.BASE_URL)
                .defaultHeader(GeminiLlmClient.API_KEY_HEADER, apiKey).build();
        this.model = model;
        this.objectMapper = objectMapper;
        this.parser = new PlanDraftResponseParser(objectMapper);
        this.systemInstruction = loadPrompt();
    }

    @Override
    public PlanDraftResult draftPlan(PlanDraftRequest request) {
        String requestBody = buildRequestBody(request);
        for (int attempt = 1; ; attempt++) {
            String responseBody = call(requestBody);
            try {
                return parser.parse(extractText(responseBody), request);
            } catch (MalformedLlmResponseException e) {
                log.warn("Gemini 계획 초안 응답 형식이 맞지 않아요. attempt={}, reason={}", attempt, e.getMessage());
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.AI_DRAFT_FAILED, e);
                }
            }
        }
    }

    private String call(String requestBody) {
        try {
            return restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("Gemini 계획 초안 호출이 실패했어요. status={}", e.getStatusCode().value());
            throw new BusinessException(ErrorCode.AI_DRAFT_FAILED, e);
        } catch (RestClientException e) {
            log.warn("Gemini 계획 초안 호출이 실패했어요(시간 초과·연결 실패). error={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_DRAFT_FAILED, e);
        }
    }

    String buildRequestBody(PlanDraftRequest request) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("city", request.city());
        input.put("dayCount", request.dayCount());
        ArrayNode candidates = input.putArray("candidates");
        for (PlanDraftRequest.Candidate candidate : request.candidates()) {
            ObjectNode item = candidates.addObject();
            item.put("placeId", candidate.placeId());
            item.put("category", candidate.category().name());
            item.put("name", candidate.name());
            item.put("latitude", candidate.latitude());
            item.put("longitude", candidate.longitude());
            ArrayNode keyPoints = item.putArray("keyPoints");
            candidate.keyPoints().forEach(keyPoints::add);
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
        ObjectNode userContent = root.putArray("contents").addObject();
        userContent.put("role", "user");
        userContent.putArray("parts").addObject()
                .put("text", "아래 계획 정보와 후보 카드로 여행 계획 초안을 만들어 줘.\n" + input);
        root.putObject("generationConfig").put("responseMimeType", "application/json");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_DRAFT_FAILED, e);
        }
    }

    /** candidates[0].content.parts의 text를 이어 붙여요. 생각 과정(thought) 조각은 빼요. */
    private String extractText(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody == null ? "" : responseBody);
        } catch (JsonProcessingException e) {
            throw new MalformedLlmResponseException("Gemini 응답이 JSON이 아니에요", e);
        }
        JsonNode parts = root == null ? null : root.path("candidates").path(0).path("content").path("parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            throw new MalformedLlmResponseException("응답에 내용이 없어요");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
                text.append(part.path("text").asText());
            }
        }
        return text.toString();
    }

    private static String loadPrompt() {
        try (InputStream in = new ClassPathResource(PROMPT_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽지 못했어요: " + PROMPT_PATH, e);
        }
    }
}
