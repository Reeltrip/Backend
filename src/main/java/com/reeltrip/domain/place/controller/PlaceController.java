package com.reeltrip.domain.place.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.place.dto.PlaceCreateRequest;
import com.reeltrip.domain.place.dto.PlaceCreateResponse;
import com.reeltrip.domain.place.dto.PlaceListRequest;
import com.reeltrip.domain.place.dto.PlaceListResponse;
import com.reeltrip.domain.place.dto.PlaceResponse;
import com.reeltrip.domain.place.dto.PlaceSortType;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.service.PlaceService;
import com.reeltrip.global.security.LoginUser;

import jakarta.validation.Valid;

/** POST/GET /places, GET/PATCH/DELETE /places/{placeId}. 소유: place 팀원 */
@RestController
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    /** S2-1 "모두 저장", S3-3 직접 추가 */
    @PostMapping("/places")
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceCreateResponse createPlaces(@LoginUser Long userId, @Valid @RequestBody PlaceCreateRequest request) {
        return placeService.createPlaces(userId, request);
    }

    /** S3-1 보관함, 여행지역 선택의 구역 주변 카드 */
    @GetMapping("/places")
    public PlaceListResponse getPlaces(@LoginUser Long userId,
                                       @RequestParam(required = false) Category category,
                                       @RequestParam(required = false) Double latitude,
                                       @RequestParam(required = false) Double longitude,
                                       @RequestParam(defaultValue = "1500") int radiusM,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(defaultValue = "LATEST") PlaceSortType sortType) {
        return placeService.getPlaces(userId,
                new PlaceListRequest(category, latitude, longitude, radiusM, page, size, sortType));
    }

    /** S3-2 카드 상세 */
    @GetMapping("/places/{placeId}")
    public PlaceResponse getPlace(@LoginUser Long userId, @PathVariable Long placeId) {
        return placeService.getPlace(userId, placeId);
    }

    /** S2-2·S3-2 카드 수정. 들어온 필드만 바꾸고, null로 온 필드는 비워요. */
    @PatchMapping("/places/{placeId}")
    public PlaceResponse modifyPlace(@LoginUser Long userId, @PathVariable Long placeId,
                                     @RequestBody JsonNode body) {
        return placeService.modifyPlace(userId, placeId, body);
    }

    /** S3-2 카드 삭제 */
    @DeleteMapping("/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePlace(@LoginUser Long userId, @PathVariable Long placeId) {
        placeService.removePlace(userId, placeId);
    }
}
