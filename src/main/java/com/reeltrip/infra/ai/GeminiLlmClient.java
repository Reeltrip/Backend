package com.reeltrip.infra.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.fetcher.FetchedContent;

/**
 * Gemini generateContent로 링크 내용 → 카드 초안 (S1-2 분석, S2-1 생성 결과).
 * 유튜브는 영상 주소를 file_data로 넘겨 Gemini가 영상을 직접 보게 하고, 그 밖은 BE 1이 가져온 본문을 넘겨요.
 * 형식이 깨진 응답만 한 번 다시 호출하고, 시간 초과·4xx·5xx는 바로 AI_EXTRACTION_FAILED예요.
 */
public class GeminiLlmClient implements LlmClient {

    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    static final String API_KEY_HEADER = "x-goog-api-key";
    private static final int MAX_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
    // 쇼츠·짧은 주소를 Gemini 문서 예시와 같은 watch?v= 모양으로 바꿔요.
    private static final Pattern YOUTUBE_ID = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?(?:youtube\\.com/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})");

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;
    private final ExtractionPrompt prompt;
    private final ExtractionResponseParser parser;

    /** builder에는 시간 제한이 걸린 요청 팩토리를 미리 넣어서 넘겨요 (GeminiConfig). 테스트는 가짜 서버를 묶어 넘겨요. */
    public GeminiLlmClient(RestClient.Builder builder, String apiKey, String model, ObjectMapper objectMapper,
            ExtractionPrompt prompt) {
        // 키는 주소가 아니라 헤더로 보내서 접속 기록에 남지 않게 해요.
        this.restClient = builder.baseUrl(BASE_URL).defaultHeader(API_KEY_HEADER, apiKey).build();
        this.model = model;
        this.objectMapper = objectMapper;
        this.prompt = prompt;
        this.parser = new ExtractionResponseParser(objectMapper);
    }

    @Override
    public ExtractionResult extractCards(FetchedContent content) {
        boolean isVideo = content.platform() == Platform.YOUTUBE;
        if (isVideo ? isBlank(content.url()) : isBlank(content.text())) {
            // 넘길 내용이 없으면 부르지 않아요 (S1-3 실패 화면).
            throw new BusinessException(ErrorCode.AI_EXTRACTION_FAILED);
        }
        String requestBody = buildRequestBody(content, isVideo);

        for (int attempt = 1; ; attempt++) {
            String responseBody = call(requestBody, content.platform());
            try {
                return parser.parse(extractText(responseBody), content.url(), isVideo);
            } catch (MalformedLlmResponseException e) {
                log.warn("Gemini 응답 형식이 맞지 않아요. platform={}, attempt={}, reason={}",
                        content.platform(), attempt, e.getMessage());
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.AI_EXTRACTION_FAILED, e);
                }
            }
        }
    }

    private String call(String requestBody, Platform platform) {
        try {
            return restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 응답 본문에는 요청 내용이 섞일 수 있어 상태 코드만 남겨요.
            log.warn("Gemini 호출이 실패했어요. platform={}, status={}", platform, e.getStatusCode().value());
            throw new BusinessException(ErrorCode.AI_EXTRACTION_FAILED, e);
        } catch (RestClientException e) {
            log.warn("Gemini 호출이 실패했어요(시간 초과·연결 실패). platform={}, error={}",
                    platform, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_EXTRACTION_FAILED, e);
        }
    }

    String buildRequestBody(FetchedContent content, boolean isVideo) {
        ObjectNode root = objectMapper.createObjectNode();

        root.putObject("systemInstruction").putArray("parts").addObject().put("text", prompt.systemInstruction());

        ObjectNode userContent = root.putArray("contents").addObject();
        userContent.put("role", "user");
        ArrayNode parts = userContent.putArray("parts");
        if (isVideo) {
            parts.addObject().putObject("file_data").put("file_uri", normalizeYoutubeUrl(content.url()));
            parts.addObject().put("text", prompt.videoRequest());
        } else {
            parts.addObject().put("text", prompt.textRequest(content));
        }

        root.putObject("generationConfig").put("responseMimeType", "application/json");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_EXTRACTION_FAILED, e);
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
            String finishReason = root == null ? null : root.path("candidates").path(0).path("finishReason").asText(null);
            throw new MalformedLlmResponseException("응답에 내용이 없어요. finishReason=" + finishReason);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
                text.append(part.path("text").asText());
            }
        }
        return text.toString();
    }

    static String normalizeYoutubeUrl(String url) {
        Matcher matcher = YOUTUBE_ID.matcher(url.strip());
        return matcher.find() ? "https://www.youtube.com/watch?v=" + matcher.group(1) : url.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
