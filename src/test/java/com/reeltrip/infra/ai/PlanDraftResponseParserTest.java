package com.reeltrip.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.entity.Category;

class PlanDraftResponseParserTest {

    private final PlanDraftResponseParser parser = new PlanDraftResponseParser(new ObjectMapper());

    // 1~3 맛집·카페·관광지, 10 숙소
    private final PlanDraftRequest request = new PlanDraftRequest("오사카", 2, List.of(
            candidate(1L, Category.FOOD), candidate(2L, Category.CAFE), candidate(3L, Category.SIGHTSEEING),
            candidate(10L, Category.STAY)));

    private static PlanDraftRequest.Candidate candidate(Long id, Category category) {
        return new PlanDraftRequest.Candidate(id, category, "장소" + id, 34.66, 135.50, List.of());
    }

    @Test
    @DisplayName("AI 초안 응답은 숙소와 일자별 방문 순서로 바뀐다.")
    void parse_normal() {
        // given
        String json = """
                { "stayPlaceIds": [10], "days": [
                  { "dayNumber": 1, "placeIds": [3, 1] }, { "dayNumber": 2, "placeIds": [2] } ] }
                """;
        // when
        PlanDraftResult result = parser.parse(json, request);
        // then
        assertThat(result.stayPlaceIds()).containsExactly(10L);
        assertThat(result.days()).containsExactly(List.of(3L, 1L), List.of(2L));
    }

    @Test
    @DisplayName("AI가 지어낸 장소, 중복 장소, 노드 자리의 숙소, 숙소 자리의 맛집, 없는 일차는 빼고 받는다.")
    void parse_dropsInvalidPlaces() {
        // given: 999는 후보에 없음, 1은 두 번, 10(숙소)이 노드에, 1(맛집)이 숙소에, 5일차는 없음
        String json = """
                { "stayPlaceIds": [1, 10], "days": [
                  { "dayNumber": 1, "placeIds": [999, 1, 10, "2"] },
                  { "dayNumber": 2, "placeIds": [1, 3] },
                  { "dayNumber": 5, "placeIds": [3] } ] }
                """;
        // when
        PlanDraftResult result = parser.parse(json, request);
        // then
        assertThat(result.stayPlaceIds()).containsExactly(10L);
        assertThat(result.days()).containsExactly(List.of(1L, 2L), List.of(3L));
    }

    @Test
    @DisplayName("빠진 일차는 빈 목록으로 채워 일자 수만큼 돌려준다.")
    void parse_missingDayIsEmpty() {
        // given: 1일차만 있음
        String json = """
                ```json
                { "days": [ { "dayNumber": 1, "placeIds": [1] } ] }
                ```
                """;
        // when
        PlanDraftResult result = parser.parse(json, request);
        // then
        assertThat(result.stayPlaceIds()).isEmpty();
        assertThat(result.days()).containsExactly(List.of(1L), List.of());
    }

    @Test
    @DisplayName("days가 없거나 JSON이 아니면 형식이 깨진 응답으로 본다.")
    void parse_malformed() {
        // given / when / then
        assertThatThrownBy(() -> parser.parse("{ \"stayPlaceIds\": [] }", request))
                .isInstanceOf(MalformedLlmResponseException.class);
        assertThatThrownBy(() -> parser.parse("초안을 만들 수 없어요", request))
                .isInstanceOf(MalformedLlmResponseException.class);
    }
}
