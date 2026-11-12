package com.reeltrip.infra.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.entity.Category;

/**
 * LLM이 낸 계획 초안 JSON을 검사해 PlanDraftResult로 바꿔요.
 * LLM이 지어낸 id나 규칙에 어긋난 카드는 조용히 빼요. 형식 자체가 깨졌을 때만 MalformedLlmResponseException.
 */
public class PlanDraftResponseParser {

    /** 하루에 넣는 노드 최대 수. 너무 빡빡한 일정을 막아요. */
    static final int MAX_NODES_PER_DAY = 8;
    /** 숙소 최대 수 */
    static final int MAX_STAYS = 3;

    private static final Pattern CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\s*(.*?)\\s*```$", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public PlanDraftResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlanDraftResult parse(String json, PlanDraftRequest request) {
        JsonNode root = readRoot(json);
        JsonNode days = root.get("days");
        if (days == null || !days.isArray()) {
            throw new MalformedLlmResponseException("days 배열이 없어요");
        }
        Map<Long, PlanDraftRequest.Candidate> candidates = request.candidates().stream()
                .collect(Collectors.toMap(PlanDraftRequest.Candidate::placeId, Function.identity(), (a, b) -> a));

        List<Long> stays = new ArrayList<>();
        for (Long placeId : ids(root.get("stayPlaceIds"))) {
            PlanDraftRequest.Candidate candidate = candidates.get(placeId);
            if (candidate != null && candidate.category() == Category.STAY && !stays.contains(placeId)
                    && stays.size() < MAX_STAYS) {
                stays.add(placeId);
            }
        }

        List<List<Long>> result = new ArrayList<>();
        for (int i = 0; i < request.dayCount(); i++) {
            result.add(new ArrayList<>());
        }
        Set<Long> used = new HashSet<>();
        for (JsonNode day : days) {
            int dayNumber = day.path("dayNumber").asInt(0);
            if (dayNumber < 1 || dayNumber > request.dayCount()) {
                continue;
            }
            List<Long> nodes = result.get(dayNumber - 1);
            for (Long placeId : ids(day.get("placeIds"))) {
                PlanDraftRequest.Candidate candidate = candidates.get(placeId);
                // 같은 장소는 한 계획에 한 번만, 숙소는 노드가 아니라 숙소로만 (decisions.md)
                boolean isNode = candidate != null && candidate.category().isPlace()
                        && candidate.category() != Category.STAY;
                if (isNode && !used.contains(placeId) && nodes.size() < MAX_NODES_PER_DAY) {
                    nodes.add(placeId);
                    used.add(placeId);
                }
            }
        }
        return new PlanDraftResult(List.copyOf(stays), result.stream().map(List::copyOf).toList());
    }

    private JsonNode readRoot(String json) {
        if (json == null || json.isBlank()) {
            throw new MalformedLlmResponseException("응답이 비어 있어요");
        }
        String body = json.strip();
        Matcher fence = CODE_FENCE.matcher(body);
        if (fence.matches()) {
            body = fence.group(1);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new MalformedLlmResponseException("JSON 객체가 아니에요");
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new MalformedLlmResponseException("JSON이 아니에요", e);
        }
    }

    /** 숫자 또는 숫자 문자열 배열만 받아요. 순서를 지키고 중복은 빼요. */
    private static List<Long> ids(JsonNode node) {
        Set<Long> ids = new LinkedHashSet<>();
        if (node == null || !node.isArray()) {
            return List.of();
        }
        for (JsonNode item : node) {
            if (item.isIntegralNumber()) {
                ids.add(item.asLong());
            } else if (item.isTextual() && item.asText().strip().matches("\\d+")) {
                ids.add(Long.valueOf(item.asText().strip()));
            }
        }
        return List.copyOf(ids);
    }
}
