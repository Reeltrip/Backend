package com.reeltrip.infra.map;

import java.util.List;

public record MapsPlace(String googlePlaceId, String name, String address, Double latitude, Double longitude,
        Integer reviewCount, List<String> openingHours, String googleMapsUri, List<Attribution> attributions) {
    public record Attribution(String displayName, String uri) { }
}
