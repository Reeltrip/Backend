package com.reeltrip.domain.place.controller;

import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.LoginUser;
import com.reeltrip.infra.map.GooglePlacePhotoClient;
import com.reeltrip.infra.map.MapsPlace;
import com.reeltrip.infra.map.PlacePhotoClient;
import io.swagger.v3.oas.annotations.media.Schema;

// S4-3 숙소 검색 등에서 고른 검색 결과의 지도 마커 사진 (FE 이슈 #74)
@RestController
public class PlacePhotoController {
    private final PlacePhotoClient client;
    private final UserRepository users;
    public PlacePhotoController(PlacePhotoClient client,UserRepository users) { this.client=client; this.users=users; }

    @GetMapping("/place-photos")
    public ResponseEntity<PhotoResponse> getPlacePhoto(@LoginUser Long userId,@RequestParam String googlePlaceId) {
        if(!users.existsById(userId)) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
        if(!GooglePlacePhotoClient.PLACE_ID.matcher(googlePlaceId).matches()) { throw new BusinessException(ErrorCode.INVALID_INPUT); }
        try {
            var photo=client.findPhoto(googlePlaceId);
            // 사진 주소는 Google이 잠깐만 열어 주는 값이라 브라우저도 저장하지 않게 해요.
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PhotoResponse(photo.photoUrl(),photo.attributions()));
        } catch(IOException | RuntimeException e) { throw new BusinessException(ErrorCode.PLACE_SEARCH_FAILED); }
    }

    public record PhotoResponse(
            @Schema(nullable=true,description="Google 장소 사진 주소예요. 사진이 없으면 null이고, 잠깐만 유효해서 저장하면 안 돼요.") String photoUrl,
            @Schema(description="사진을 올린 사람이에요. 사진과 함께 표시해야 해요.") List<MapsPlace.Attribution> attributions) { }
}
