package com.reeltrip.domain.place.entity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 카드에서 사용자가 쓰거나 고칠 수 있는 값 묶음. Place를 만들거나 통째로 고칠 때 넘겨요.
 * 필수 항목 검사는 PlaceService가 해요 (엔티티는 검사하지 않음).
 */
public record PlaceContent(
        Category category,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
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
    /** 사진 주소만 바꾼 복사본 (#87 저장소로 옮긴 주소를 넣을 때) */
    public PlaceContent withImageUrl(String newImageUrl) {
        return new PlaceContent(category, name, address, latitude, longitude, googlePlaceId, city, newImageUrl, sourceUrl,
                sourceTimestampSec, keyPoints, priceText, reviewCount, openingHours, accessText, memo, subType,
                applicableRegion, cost, benefit, method, placeText, caution);
    }
}
