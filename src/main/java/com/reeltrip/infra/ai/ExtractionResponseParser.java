package com.reeltrip.infra.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.entity.Category;

/**
 * LLM이 낸 JSON 문자열(docs/api-spec.md "LLM 출력 형식") → ExtractionResult.
 * 최상위 모양이 깨지면 MalformedLlmResponseException, 카드 한 장이 이상하면 그 카드만 버려요.
 * 글자 수는 POST /places 검사와 place 컬럼 길이에 맞춰 잘라서, S2-1 "모두 저장"이 길이 때문에 실패하지 않게 해요.
 */
public class ExtractionResponseParser {

    // POST /places 검사 (decisions.md "서버에서 검사할 규칙")
    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_PRICE_TEXT_LENGTH = 50;
    static final int MAX_MEMO_LENGTH = 50;
    // 그 밖은 docs/schema.sql 컬럼 길이
    static final int MAX_KEY_POINT_LENGTH = 100;
    static final int MAX_CITY_LENGTH = 100;
    static final int MAX_ACCESS_TEXT_LENGTH = 255;
    static final int MAX_SUB_TYPE_LENGTH = 30;
    static final int MAX_APPLICABLE_REGION_LENGTH = 100;
    static final int MAX_COST_LENGTH = 255;
    static final int MAX_PLACE_TEXT_LENGTH = 255;

    private static final Pattern CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\s*(.*?)\\s*```$", Pattern.DOTALL);
    private static final Pattern MIN_SEC = Pattern.compile("^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$");

    private final ObjectMapper objectMapper;

    public ExtractionResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param json      LLM이 낸 JSON 문자열
     * @param sourceUrl 각 카드의 원본 링크
     * @param isVideo   유튜브일 때만 sourceTimestampSec을 받아요
     */
    public ExtractionResult parse(String json, String sourceUrl, boolean isVideo) {
        JsonNode root = readRoot(json);
        JsonNode cardsNode = root.get("cards");
        if (cardsNode == null || !cardsNode.isArray()) {
            throw new MalformedLlmResponseException("cards 배열이 없어요");
        }

        String city = text(root.get("city"), MAX_CITY_LENGTH);
        List<PlaceDraft> cards = new ArrayList<>();
        for (JsonNode cardNode : cardsNode) {
            PlaceDraft draft = toDraft(cardNode, city, sourceUrl, isVideo);
            if (draft != null) {
                cards.add(draft);
            }
        }
        return new ExtractionResult(city, List.copyOf(cards));
    }

    private JsonNode readRoot(String json) {
        if (json == null || json.isBlank()) {
            throw new MalformedLlmResponseException("응답이 비었어요");
        }
        String body = json.strip();
        // responseMimeType을 줘도 코드 블록 표시가 붙어 올 때가 있어 벗겨요.
        Matcher fence = CODE_FENCE.matcher(body);
        if (fence.matches()) {
            body = fence.group(1);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new MalformedLlmResponseException("최상위가 JSON 객체가 아니에요");
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new MalformedLlmResponseException("JSON이 아니에요", e);
        }
    }

    /** 버릴 카드면 null */
    private PlaceDraft toDraft(JsonNode node, String city, String sourceUrl, boolean isVideo) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Category category = category(node.get("category"));
        String name = text(node.get("name"), MAX_NAME_LENGTH);
        // 모르는 카테고리는 화면에 둘 곳이 없고, 이름이 없으면 장소 검색(BE 1 보강)을 못 해요.
        if (category == null || name == null) {
            return null;
        }

        String memo = text(node.get("memo"), MAX_MEMO_LENGTH);
        Integer timestampSec = isVideo ? timestamp(node.get("sourceTimestampSec")) : null;

        if (category.isPlace()) {
            // 교통·팁 전용 항목은 장소 카드에서 비워요 (decisions.md DB).
            return new PlaceDraft(
                    null, category, name, null, null, null, null, city, null, sourceUrl, timestampSec,
                    keyPoints(node.get("keyPoints")),
                    text(node.get("priceText"), MAX_PRICE_TEXT_LENGTH),
                    null, null,
                    text(node.get("accessText"), MAX_ACCESS_TEXT_LENGTH),
                    memo,
                    null, null, null, null, null, null, null);
        }
        return new PlaceDraft(
                null, category, name, null, null, null, null, city, null, sourceUrl, timestampSec,
                List.of(), null, null, null, null, memo,
                text(node.get("subType"), MAX_SUB_TYPE_LENGTH),
                text(node.get("applicableRegion"), MAX_APPLICABLE_REGION_LENGTH),
                text(node.get("cost"), MAX_COST_LENGTH),
                text(node.get("benefit"), Integer.MAX_VALUE),
                text(node.get("method"), Integer.MAX_VALUE),
                text(node.get("placeText"), MAX_PLACE_TEXT_LENGTH),
                text(node.get("caution"), Integer.MAX_VALUE));
    }

    private static Category category(JsonNode node) {
        String value = text(node, Integer.MAX_VALUE);
        if (value == null) {
            return null;
        }
        try {
            return Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> keyPoints(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> points = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String point = text(item, MAX_KEY_POINT_LENGTH);
                if (point != null && points.size() < Category.MAX_KEY_POINTS) {
                    points.add(point);
                }
            }
        } else {
            String point = text(node, MAX_KEY_POINT_LENGTH);
            if (point != null) {
                points.add(point);
            }
        }
        return List.copyOf(points);
    }

    /** 문자열·숫자만 받아요. 빈 값과 "null" 글자는 null. */
    private static String text(JsonNode node, int maxLength) {
        if (node == null || !(node.isTextual() || node.isNumber())) {
            return null;
        }
        String value = node.asText().strip();
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength).strip() : value;
    }

    /** 42 또는 "42", "1:23" 모양을 초로. 그 밖은 null */
    private static Integer timestamp(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.canConvertToInt() && node.asInt() >= 0 ? node.asInt() : null;
        }
        String value = text(node, Integer.MAX_VALUE);
        if (value == null) {
            return null;
        }
        if (value.matches("\\d{1,7}")) {
            return Integer.parseInt(value);
        }
        Matcher matcher = MIN_SEC.matcher(value);
        if (matcher.matches()) {
            int hours = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
            return hours * 3600 + Integer.parseInt(matcher.group(2)) * 60 + Integer.parseInt(matcher.group(3));
        }
        return null;
    }
}
