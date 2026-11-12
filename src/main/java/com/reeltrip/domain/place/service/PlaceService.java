package com.reeltrip.domain.place.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.place.dto.PlaceCreateRequest;
import com.reeltrip.domain.place.dto.PlaceCreateResponse;
import com.reeltrip.domain.place.dto.PlaceListRequest;
import com.reeltrip.domain.place.dto.PlaceListResponse;
import com.reeltrip.domain.place.dto.PlaceResponse;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.global.config.TimeConfig;

/** 요약카드 (S2-1, S3-1, S3-2, S3-3). 소유: place 팀원 */
@Service
public class PlaceService {

    /** POST /places */
    public PlaceCreateResponse createPlaces(Long userId, PlaceCreateRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return new PlaceCreateResponse(List.of(new PlaceCreateResponse.Item(101L, "하카타 잇소", Category.FOOD)));
    }

    /** GET /places */
    public PlaceListResponse getPlaces(Long userId, PlaceListRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        Map<Category, Long> counts = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            counts.put(category, 0L);
        }
        counts.put(Category.FOOD, 1L);
        PlaceListResponse.Item item = new PlaceListResponse.Item(101L, Category.FOOD, "하카타 잇소",
                "福岡市博多区博多駅東3-1-6", 33.5889, 130.4245, "https://storage.googleapis.com/sample.jpg",
                "¥960~980", 6500);
        return new PlaceListResponse(List.of(item), counts, request.page(), request.size(), 1);
    }

    /** GET /places/{placeId} */
    public PlaceResponse getPlace(Long userId, Long placeId) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return fakePlace(placeId);
    }

    /** PATCH /places/{placeId} */
    public PlaceResponse modifyPlace(Long userId, Long placeId, JsonNode body) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return fakePlace(placeId);
    }

    /** DELETE /places/{placeId} */
    public void removePlace(Long userId, Long placeId) {
        // TODO: 가짜 응답, 실제 구현으로 교체
    }

    /** GET /users/me의 보관함 카드 수 (BE 1이 씀) */
    public long countPlaces(Long userId) {
        return 0; // TODO: 가짜 응답, 실제 구현으로 교체
    }

    private PlaceResponse fakePlace(Long placeId) {
        OffsetDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0).atZone(TimeConfig.SEOUL).toOffsetDateTime();
        return new PlaceResponse(placeId, 31L, Category.FOOD, "하카타 잇소", "福岡市博多区博多駅東3-1-6",
                33.5889, 130.4245, "ChIJ-sample", "후쿠오카", "https://storage.googleapis.com/sample.jpg",
                "https://www.instagram.com/reel/sample", null, List.of("돈코츠 라멘"), "¥960~980", 6500,
                "11:00-24:00", "하카타역 동쪽 도보 약 6~7분", null,
                null, null, null, null, null, null, null, now, now);
    }
}
