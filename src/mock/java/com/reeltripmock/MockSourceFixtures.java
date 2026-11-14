package com.reeltripmock;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.FakeLlmClient;
import com.reeltrip.infra.fetcher.FetchedContent;

@Component
class MockSourceFixtures {

    private final Map<Long, Fixture> sources = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(100);
    private final FakeLlmClient llm = new FakeLlmClient();
    private final ObjectMapper mapper;
    private final Clock clock;

    MockSourceFixtures(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
        sources.put(1L, new Fixture(List.of("https://www.youtube.com/shorts/mock-success"), "success"));
        sources.put(2L, new Fixture(List.of("https://www.youtube.com/shorts/mock-success",
                "https://www.instagram.com/reel/mock-failure"), "partial"));
        sources.put(3L, new Fixture(List.of("https://www.instagram.com/reel/mock-failure"), "failed"));
    }

    Map<String, Object> create(List<String> urls, String scenario) {
        if (!List.of("success", "partial", "failed", "analyzing").contains(scenario)
                || ("partial".equals(scenario) && urls.size() < 2)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        long sourceId = nextId.getAndIncrement();
        sources.put(sourceId, new Fixture(List.copyOf(urls), scenario));
        return Map.of("sourceId", sourceId, "status", "PENDING");
    }

    Map<String, Object> get(long sourceId, String imageUrl) {
        Fixture fixture = sources.get(sourceId);
        if (fixture == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<Map<String, Object>> links = new ArrayList<>();
        boolean analyzing = "analyzing".equals(fixture.scenario());
        for (int index = 0; index < fixture.urls().size(); index++) {
            String url = fixture.urls().get(index);
            long sourceLinkId = sourceId * 10 + index;
            boolean failed = "failed".equals(fixture.scenario())
                    || ("partial".equals(fixture.scenario()) && index > 0);
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("sourceLinkId", sourceLinkId);
            link.put("url", url);
            link.put("status", analyzing ? "ANALYZING" : failed ? "FAILED" : "DONE");
            link.put("places", analyzing || failed ? List.of() : cards(url, sourceLinkId, imageUrl));
            if (failed && !analyzing) {
                link.put("code", ErrorCode.SOURCE_FETCH_FAILED.name());
                link.put("message", ErrorCode.SOURCE_FETCH_FAILED.getMessage());
            }
            links.add(link);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sourceId", sourceId);
        response.put("title", analyzing ? null : "후쿠오카 여행");
        response.put("status", analyzing ? "ANALYZING" : "failed".equals(fixture.scenario()) ? "FAILED" : "DONE");
        response.put("sourceLinks", links);
        return response;
    }

    List<Map<String, Object>> recent() {
        return List.of(
                Map.of("sourceId", 2, "title", "후쿠오카 여행", "status", "DONE",
                        "createdAt", OffsetDateTime.now(clock).toString()),
                Map.of("sourceId", 1, "title", "후쿠오카 여행", "status", "DONE",
                        "createdAt", OffsetDateTime.now(clock).minusMinutes(1).toString()));
    }

    private List<Map<String, Object>> cards(String url, long sourceLinkId, String imageUrl) {
        Platform platform = url.contains("youtu") ? Platform.YOUTUBE : Platform.ETC;
        return llm.extractCards(new FetchedContent(platform, url, null, null)).cards().stream().map(card -> {
            Map<String, Object> fields = mapper.convertValue(card,
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            fields.put("sourceLinkId", sourceLinkId);
            if (card.category().isPlace()) {
                fields.put("address", "福岡市博多区博多駅東3-1-6");
                fields.put("latitude", 33.5889);
                fields.put("longitude", 130.4245);
                fields.put("googlePlaceId", "mock-place-1");
                fields.put("imageUrl", imageUrl);
                fields.put("reviewCount", 6500);
                fields.put("openingHours", "11:00-24:00");
            }
            return fields;
        }).toList();
    }

    private record Fixture(List<String> urls, String scenario) { }
}

