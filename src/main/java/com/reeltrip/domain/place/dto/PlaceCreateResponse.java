package com.reeltrip.domain.place.dto;

import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;

/** POST /places 응답. 요청 순서대로 저장한 카드 */
public record PlaceCreateResponse(List<Item> places) {

    public record Item(Long placeId, String name, Category category) {

        public static Item from(Place place) {
            return new Item(place.getPlaceId(), place.getName(), place.getCategory());
        }
    }
}
