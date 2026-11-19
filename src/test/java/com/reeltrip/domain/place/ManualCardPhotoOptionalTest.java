package com.reeltrip.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

/** S4-3 숙소 검색·S3-3 직접 추가: 사용자가 직접 만든 장소 카드는 사진이 없어도 저장돼요 (#88) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManualCardPhotoOptionalTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;

    User me;
    String bearer;

    @BeforeEach
    void setUp() {
        me = userRepository.save(TestFixtures.user("manual-photo@test.com"));
        bearer = "Bearer " + jwtProvider.createAccessToken(me.getUserId());
    }

    @Test
    @DisplayName("검색으로 고른 숙소처럼 사진 없이 직접 추가한 장소 카드도 저장된다.")
    void manualPlaceCardWithoutPhoto_saved() throws Exception {
        // given: FE가 숙소 검색 결과로 보내는 값 그대로
        Map<String, Object> lodging = new HashMap<>();
        lodging.put("category", "STAY");
        lodging.put("name", "호텔 닛코 오사카");
        lodging.put("address", "1-chōme-3-3 Nishishinsaibashi, Chuo Ward, Osaka, 542-0086 일본");
        lodging.put("latitude", 34.6731);
        lodging.put("longitude", 135.4995);
        lodging.put("googlePlaceId", "ChIJ-test-lodging");
        // when
        postCards(List.of(lodging)).andExpect(status().isCreated()).andExpect(jsonPath("$.places[0].placeId").isNumber());
        // then
        Place saved = placeRepository.findAllByUser_UserIdOrderByCreatedAtDescPlaceIdDesc(me.getUserId()).getFirst();
        assertThat(saved.getName()).isEqualTo("호텔 닛코 오사카");
        assertThat(saved.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("직접 추가한 장소 카드도 주소와 좌표는 여전히 필수다.")
    void manualPlaceCardWithoutAddressOrCoordinate_invalid() throws Exception {
        // given
        var noAddress = PlaceTestFixtures.foodCard("주소 없음"); noAddress.remove("imageUrl"); noAddress.remove("address");
        var noCoordinate = PlaceTestFixtures.foodCard("좌표 없음"); noCoordinate.remove("imageUrl"); noCoordinate.remove("latitude");
        // when / then
        postCards(List.of(noAddress)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        postCards(List.of(noCoordinate)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(placeRepository.countByUser_UserId(me.getUserId())).isZero();
    }

    @Test
    @DisplayName("사진 없이 저장한 직접 추가 카드는 사진 없이도 수정할 수 있다.")
    void manualPlaceCardWithoutPhoto_canBeModified() throws Exception {
        // given
        var card = PlaceTestFixtures.foodCard("사진 없는 가게"); card.remove("imageUrl");
        postCards(List.of(card)).andExpect(status().isCreated());
        Long placeId = placeRepository.findAllByUser_UserIdOrderByCreatedAtDescPlaceIdDesc(me.getUserId()).getFirst().getPlaceId();
        // when / then
        mockMvc.perform(patch("/places/" + placeId).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"이름 바꿈\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("이름 바꿈"));
    }

    private org.springframework.test.web.servlet.ResultActions postCards(List<Map<String, Object>> cards) throws Exception {
        return mockMvc.perform(post("/places").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("places", cards))));
    }
}
