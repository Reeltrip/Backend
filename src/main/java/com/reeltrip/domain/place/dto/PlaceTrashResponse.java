package com.reeltrip.domain.place.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;

/** GET /places/trash 응답 (Figma 3-3 휴지통 보기, "총 n개의 카드") */
public record PlaceTrashResponse(List<Item> places, long totalCount) {

    public record Item(
            Long placeId,
            Category category,
            String name,
            String address,
            String imageUrl,
            List<String> keyPoints,
            OffsetDateTime deletedAt
    ) {

        public static Item from(Place place) {
            return new Item(place.getPlaceId(), place.getCategory(), place.getName(), place.getAddress(),
                    place.getImageUrl(), place.getKeyPointContents(), PlaceResponse.toSeoul(place.getDeletedAt()));
        }
    }
}
