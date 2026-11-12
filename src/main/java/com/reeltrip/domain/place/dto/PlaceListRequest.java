package com.reeltrip.domain.place.dto;

import com.reeltrip.domain.place.entity.Category;

/**
 * GET /places 쿼리 (S3-1 보관함, 여행지역 선택의 구역 주변 카드).
 * category가 null이면 전체, latitude·longitude가 둘 다 있으면 radiusM 안의 카드만.
 */
public record PlaceListRequest(
        Category category,
        Double latitude,
        Double longitude,
        int radiusM,
        int page,
        int size,
        PlaceSortType sortType
) {

    public boolean hasRadiusFilter() {
        return latitude != null && longitude != null;
    }
}
