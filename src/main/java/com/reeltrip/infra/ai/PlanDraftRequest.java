package com.reeltrip.infra.ai;

import java.util.List;

import com.reeltrip.domain.place.entity.Category;

/**
 * AI 초안에 넘기는 계획 정보와 후보 카드. 후보는 장소 카드(isPlace)만 넣어요.
 * fixedStayPlaceIds는 사용자가 이미 정해 둔(예약한) 숙소예요. 비어 있으면 AI가 숙소 카드 중에서 골라요.
 */
public record PlanDraftRequest(String city, int dayCount, List<Candidate> candidates, List<Long> fixedStayPlaceIds) {

    public PlanDraftRequest {
        fixedStayPlaceIds = fixedStayPlaceIds == null ? List.of() : List.copyOf(fixedStayPlaceIds);
    }

    /** 정해 둔 숙소가 없는 요청 */
    public PlanDraftRequest(String city, int dayCount, List<Candidate> candidates) {
        this(city, dayCount, candidates, List.of());
    }

    public record Candidate(Long placeId, Category category, String name, Double latitude, Double longitude,
                            List<String> keyPoints) {
    }
}
