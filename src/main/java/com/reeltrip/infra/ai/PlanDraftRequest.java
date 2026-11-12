package com.reeltrip.infra.ai;

import java.util.List;

import com.reeltrip.domain.place.entity.Category;

/** AI 초안에 넘기는 계획 정보와 후보 카드. 후보는 장소 카드(isPlace)만 넣어요. */
public record PlanDraftRequest(String city, int dayCount, List<Candidate> candidates) {

    public record Candidate(Long placeId, Category category, String name, Double latitude, Double longitude,
                            List<String> keyPoints) {
    }
}
