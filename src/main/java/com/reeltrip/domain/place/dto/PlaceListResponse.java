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
            Integer reviewCount,
            // 카드 한 줄 요약에 바로 쓰는 값이에요. 목록 카드마다 상세를 따로 부르지 않게 같이 내려줘요.
            String openingHours,
            String accessText,
            String subType,
            String applicableRegion,
            String cost,
            // 사진 없는 카드의 지도 마커를 FE가 Google 사진으로 그릴 때 써요 (#91). 없으면 null
            String googlePlaceId
    ) {

        public static Item from(Place place) {
            return new Item(place.getPlaceId(), place.getCategory(), place.getName(), place.getAddress(),
                    PlaceResponse.toDouble(place.getLatitude()), PlaceResponse.toDouble(place.getLongitude()),
                    place.getImageUrl(), place.getPriceText(), place.getReviewCount(),
                    place.getOpeningHours(), place.getAccessText(),
                    place.getSubType(), place.getApplicableRegion(), place.getCost(), place.getGooglePlaceId());
        }
    }
}
