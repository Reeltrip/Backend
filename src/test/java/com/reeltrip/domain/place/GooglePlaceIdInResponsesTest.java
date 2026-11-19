package com.reeltrip.domain.place;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

/** 사진 없는 카드의 지도 마커를 FE가 Google 사진으로 그릴 수 있게 googlePlaceId를 같이 내려줘요 (#91, FE #74) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GooglePlaceIdInResponsesTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;

    String bearer;

    @BeforeEach
    void setUp() {
        User me = userRepository.save(TestFixtures.user("gpid@test.com"));
        bearer = "Bearer " + jwtProvider.createAccessToken(me.getUserId());
    }

    @Test
    @DisplayName("보관함 목록의 카드마다 googlePlaceId를 내려주고, 없는 카드는 null이다.")
    void placeList_hasGooglePlaceId() throws Exception {
        // given
        long lodging = createCard("STAY", "호텔 닛코 오사카", "ChIJ-lodging");
        createCard("FOOD", "이름만 아는 가게", null);
        // when / then
        mockMvc.perform(get("/places").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[?(@.placeId == " + lodging + ")].googlePlaceId").value("ChIJ-lodging"))
                .andExpect(jsonPath("$.places[?(@.name == '이름만 아는 가게')].googlePlaceId").value((Object) null));
    }

    @Test
    @DisplayName("계획 보기의 숙소와 노드에도 googlePlaceId를 내려줘서 공유받은 사람도 마커 사진을 볼 수 있다.")
    void planDetail_hasGooglePlaceIdOnStayAndNode() throws Exception {
        // given: 숙소와 노드가 하나씩 있는 계획
        long lodging = createCard("STAY", "호텔 닛코 오사카", "ChIJ-lodging");
        long food = createCard("FOOD", "이치미젠", "ChIJ-food");
        long planId = objectMapper.readTree(send(post("/plans"),
                Map.of("name", "오사카", "startDate", "2026-12-10", "endDate", "2026-12-10", "city", "오사카"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("planId").asLong();
        send(put("/plans/" + planId + "/stays"), Map.of("placeIds", List.of(lodging))).andExpect(status().isOk());
        send(put("/plans/" + planId + "/days/1"),
                Map.of("nodes", List.of(Map.of("placeId", food, "alternativePlaceIds", List.of())), "isCompleted", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].googlePlaceId").value("ChIJ-food"));
        // when / then
        mockMvc.perform(get("/plans/" + planId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays[0].googlePlaceId").value("ChIJ-lodging"))
                .andExpect(jsonPath("$.days[0].nodes[0].googlePlaceId").value("ChIJ-food"));
    }

    private long createCard(String category, String name, String googlePlaceId) throws Exception {
        Map<String, Object> card = new HashMap<>();
        card.put("category", category);
        card.put("name", name);
        card.put("address", "1-chōme-3-3 Nishishinsaibashi, Chuo Ward, Osaka");
        card.put("latitude", 34.6731);
        card.put("longitude", 135.4995);
        card.put("city", "오사카");
        if (googlePlaceId != null) { card.put("googlePlaceId", googlePlaceId); }
        String body = send(post("/places"), Map.of("places", List.of(card)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("places").path(0).path("placeId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }
}
