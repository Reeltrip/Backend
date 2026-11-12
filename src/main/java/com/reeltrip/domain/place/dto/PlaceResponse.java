package com.reeltrip.domain.place.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.global.config.TimeConfig;

/** 카드 상세 (GET·PATCH /places/{placeId}, S3-2). 저장 요청의 카드 한 장 필드 + placeId, createdAt, updatedAt */
public record PlaceResponse(
        Long placeId,
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
        String caution,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PlaceResponse from(Place place) {
        Long sourceLinkId = place.getSourceLink() == null ? null : place.getSourceLink().getSourceLinkId();
        return new PlaceResponse(place.getPlaceId(), sourceLinkId, place.getCategory(), place.getName(),
                place.getAddress(), toDouble(place.getLatitude()), toDouble(place.getLongitude()),
                place.getGooglePlaceId(), place.getCity(), place.getImageUrl(), place.getSourceUrl(),
                place.getSourceTimestampSec(), place.getKeyPointContents(), place.getPriceText(),
                place.getReviewCount(), place.getOpeningHours(), place.getAccessText(), place.getMemo(),
                place.getSubType(), place.getApplicableRegion(), place.getCost(), place.getBenefit(),
                place.getMethod(), place.getPlaceText(), place.getCaution(),
                toSeoul(place.getCreatedAt()), toSeoul(place.getUpdatedAt()));
    }

    static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static OffsetDateTime toSeoul(LocalDateTime value) {
        return value == null ? null : value.atZone(TimeConfig.SEOUL).toOffsetDateTime();
    }
}
