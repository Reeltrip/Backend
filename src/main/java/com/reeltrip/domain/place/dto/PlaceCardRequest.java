package com.reeltrip.domain.place.dto;

import java.util.List;

import com.reeltrip.domain.place.entity.Category;

/**
 * 카드 한 장 (POST /places의 places 항목, PATCH /places/{placeId} 본문).
 * 필수 항목은 카테고리마다 달라서 PlaceService가 검사해요.
 * sourceLinkId는 AI가 만든 카드(S2-1)만 있고, 직접 추가(S3-3)는 null이에요.
 */
public record PlaceCardRequest(
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
