package com.reeltrip.infra.map;

import java.util.List;

public record MapsPlace(String googlePlaceId, String name, String address, Double latitude, Double longitude,
        Integer reviewCount, List<String> openingHours, String googleMapsUri, List<Attribution> attributions,
        /** 화면에 그대로 보여 줄 가격 문구(예: ¥1,000~2,000, 가격대 보통). Google에 가격이 없으면 null이에요. */
        String priceText) {
    /** 가격 없이 만드는 기존 호출을 위한 생성자예요. */
    public MapsPlace(String googlePlaceId, String name, String address, Double latitude, Double longitude,
            Integer reviewCount, List<String> openingHours, String googleMapsUri, List<Attribution> attributions) {
        this(googlePlaceId, name, address, latitude, longitude, reviewCount, openingHours, googleMapsUri, attributions, null);
    }
    public record Attribution(String displayName, String uri) { }
}
