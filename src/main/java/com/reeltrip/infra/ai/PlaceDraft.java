package com.reeltrip.infra.ai;

import java.util.List;

import com.reeltrip.domain.place.entity.Category;

/**
 * 카드 초안 한 장. 필드는 POST /places 요청의 카드 한 장과 같아요 (docs/api-spec.md, decisions.md #5의 city 포함).
 * LlmClient는 category·name·keyPoints·priceText·accessText·sourceTimestampSec·memo·교통·팁 항목·city·sourceUrl만 채워요.
 * sourceLinkId, address, latitude, longitude, googlePlaceId, imageUrl, reviewCount, openingHours는 null로 넘기고
 * BE 1이 장소 보강 단계에서 채운 새 PlaceDraft를 만들어요.
 */
public record PlaceDraft(
        Long sourceLinkId,
        Category category,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String googlePlaceId,
        String city,
        String imageUrl,
        String sourceUrl,
        Integer sourceTimestampSec,
        List<String> keyPoints,
        String priceText,
        Integer reviewCount,
        String openingHours,
        String accessText,
        String memo,
        String subType,
        String applicableRegion,
        String cost,
        String benefit,
        String method,
        String placeText,
        String caution
) {
}
