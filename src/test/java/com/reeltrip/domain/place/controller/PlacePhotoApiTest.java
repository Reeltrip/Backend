package com.reeltrip.domain.place.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.map.*;
import com.reeltrip.support.TestFixtures;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PlacePhotoApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @MockitoBean PlacePhotoClient photos;

    @Test @DisplayName("검색한 장소의 사진 주소와 출처를 저장하지 않는 응답으로 돌려준다.")
    void returnsPhotoWithAttribution() throws Exception {
        // given
        when(photos.findPhoto("ChIJabc")).thenReturn(new MapsPhoto("https://lh3.googleusercontent.com/p/x",
                List.of(new MapsPlace.Attribution("홍길동","https://maps.google.com/maps/contrib/1"))));
        // when / then
        mvc.perform(get("/place-photos").param("googlePlaceId","ChIJabc").header("Authorization",token())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.photoUrl").value("https://lh3.googleusercontent.com/p/x"))
                .andExpect(jsonPath("$.attributions[0].displayName").value("홍길동"));
    }

    @Test @DisplayName("사진이 없는 장소는 실패가 아니라 빈 사진으로 돌려준다.")
    void placeWithoutPhotoIsOk() throws Exception {
        // given
        when(photos.findPhoto("ChIJabc")).thenReturn(MapsPhoto.NONE);
        // when / then
        mvc.perform(get("/place-photos").param("googlePlaceId","ChIJabc").header("Authorization",token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isEmpty()).andExpect(jsonPath("$.attributions").isEmpty());
    }

    @Test @DisplayName("이상한 장소 ID와 미인증 요청은 Google을 부르기 전에 거부한다.")
    void rejectsBadIdAndAnonymous() throws Exception {
        // given
        String token=token();
        // when / then
        mvc.perform(get("/place-photos").param("googlePlaceId","a/../b").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/place-photos").param("googlePlaceId"," ").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/place-photos").param("googlePlaceId","ChIJabc")).andExpect(status().isUnauthorized());
        verifyNoInteractions(photos);
    }

    @Test @DisplayName("사진 조회 실패는 외부 원문 없이 공통 오류로 돌려준다.")
    void hidesProviderFailures() throws Exception {
        // given
        when(photos.findPhoto(anyString())).thenThrow(new java.io.IOException("private provider message"));
        // when / then
        mvc.perform(get("/place-photos").param("googlePlaceId","ChIJabc").header("Authorization",token()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PLACE_SEARCH_FAILED"));
    }

    private String token() {
        var user=users.save(TestFixtures.user(java.util.UUID.randomUUID()+"@example.test"));
        return "Bearer "+jwt.createAccessToken(user.getUserId());
    }
}
