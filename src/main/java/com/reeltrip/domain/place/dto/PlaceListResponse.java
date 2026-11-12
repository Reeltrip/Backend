package com.reeltrip.domain.place.dto;

import java.util.List;
import java.util.Map;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;

/** GET /places 응답 (S3-1). categoryCounts는 8개 키를 0 포함 모두 담아요. */
public record PlaceListResponse(
        List<Item> places,
        Map<Category, Long> categoryCounts,
        int page,
        int size,
        long totalCount
) {

    public record Item(
            Long placeId,
            Category category,
            String name,
            String address,
            Double latitude,
            Double longitude,
            String imageUrl,
            String priceText,
            Integer reviewCount
    ) {

        public static Item from(Place place) {
            return new Item(place.getPlaceId(), place.getCategory(), place.getName(), place.getAddress(),
                    PlaceResponse.toDouble(place.getLatitude()), PlaceResponse.toDouble(place.getLongitude()),
                    place.getImageUrl(), place.getPriceText(), place.getReviewCount());
        }
    }
}
