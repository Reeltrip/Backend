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
class PlaceSearchApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @MockitoBean PlacesClient places;

    @Test @DisplayName("장소·도시·구역 결과를 같은 검색 응답과 출처 표시로 반환하고 사진 키를 내보내지 않는다.")
    void returnsLiveSearchResults() throws Exception {
        // given
        String token=token();
        when(places.search("교토")).thenReturn(List.of(new MapsPlace("id","교토","일본",35.0,135.0,null,List.of(),"https://maps.google.com/?cid=1",List.of())));
        // when / then
        mvc.perform(get("/place-search").param("query"," 교토 ").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.places[0].googlePlaceId").value("id"))
                .andExpect(jsonPath("$.places[0].latitude").value(35)).andExpect(jsonPath("$.places[0].imageUrl").isEmpty())
                .andExpect(jsonPath("$.attributionText").value("Google Maps"));
    }

    @Test @DisplayName("검색 결과 없음은 빈 목록이고 빈 검색어·미인증은 외부 호출 전에 거부한다.")
    void handlesNoResultsAndInvalidInput() throws Exception {
        // given
        String token=token(); when(places.search("없는 곳")).thenReturn(List.of());
        // when / then
        mvc.perform(get("/place-search").param("query","없는 곳").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.places").isEmpty());
        mvc.perform(get("/place-search").param("query"," ").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/place-search").param("query","교토")).andExpect(status().isUnauthorized());
        verify(places,times(1)).search(anyString());
    }

    @Test @DisplayName("검색 실패는 API 키나 외부 원문 없이 공통 오류로 반환한다.")
    void hidesProviderFailures() throws Exception {
        // given
        String token=token(); when(places.search(anyString())).thenThrow(new java.io.IOException("private provider message"));
        // when / then
        mvc.perform(get("/place-search").param("query","교토").header("Authorization",token))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PLACE_SEARCH_FAILED"));
    }
    private String token() {
        var user=users.save(TestFixtures.user(java.util.UUID.randomUUID()+"@example.test"));
        return "Bearer "+jwt.createAccessToken(user.getUserId());
    }
}
