package com.reeltrip.domain.place.dto;

import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;

/**
 * POST /places 응답. places는 요청 순서대로 저장한 카드, skipped는 필수 항목이 비어 저장하지 않은 AI 카드예요.
 * 뺄 카드가 없으면 skipped는 빈 목록이에요 (S2-1 "모두 저장").
 */
public record PlaceCreateResponse(List<Item> places, List<Skipped> skipped) {

    public record Item(Long placeId, String name, Category category) {

        public static Item from(Place place) {
            return new Item(place.getPlaceId(), place.getName(), place.getCategory());
        }
    }

    /**
     * @param index         요청 places 안의 순서 (0부터)
     * @param missingFields 빈 필수 항목의 JSON 필드 이름 (imageUrl, address, latitude, longitude, subType, applicableRegion)
     */
    public record Skipped(int index, Long sourceLinkId, String name, List<String> missingFields) {
    }
}
