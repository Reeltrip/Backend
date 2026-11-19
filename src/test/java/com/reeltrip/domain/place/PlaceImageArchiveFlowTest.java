package com.reeltrip.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.place.service.PlaceImageArchiver;
import com.reeltrip.domain.place.service.PlaceImageBackfill;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** 만료되는 외부 사진 주소가 카드에 남지 않게 하는 흐름 (#87) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlaceImageArchiveFlowTest {
    private static final String INSTAGRAM = "https://scontent-ssn1-1.cdninstagram.com/v/t51/1_n.jpg?oe=6921ABCD";
    private static final String ARCHIVED = "https://storage.googleapis.com/bucket/places/archive/a.jpg";
    private static final String YOUTUBE = "https://i.ytimg.com/vi/abc/hqdefault.jpg";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlaceImageBackfill backfill;
    @Autowired EntityManager em;
    @MockitoBean PlaceImageArchiver archiver;

    User me;

    @BeforeEach
    void setUp() {
        me = userRepository.save(TestFixtures.user("archive@test.com"));
        when(archiver.archive(anyString())).thenAnswer(call -> call.getArgument(0));
        when(archiver.needsArchive(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).contains("cdninstagram"));
        when(archiver.archive(INSTAGRAM)).thenReturn(ARCHIVED);
    }

    @Test
    @DisplayName("카드를 저장할 때 만료되는 사진 주소는 저장소 주소로 바뀌고, 같은 사진은 한 번만 옮긴다.")
    void createPlaces_replacesExpiringImageOnce() throws Exception {
        // given: 같은 인스타그램 사진을 쓰는 카드 두 장과 유튜브 사진 카드 한 장
        var first = PlaceTestFixtures.foodCard("라멘"); first.put("imageUrl", INSTAGRAM);
        var second = PlaceTestFixtures.foodCard("우동"); second.put("imageUrl", INSTAGRAM);
        var third = PlaceTestFixtures.foodCard("카레"); third.put("imageUrl", YOUTUBE);
        // when
        mockMvc.perform(post("/places").header("Authorization", "Bearer " + jwtProvider.createAccessToken(me.getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("places", List.of(first, second, third)))))
                .andExpect(status().isCreated());
        // then
        assertThat(placeRepository.findAllByUser_UserIdOrderByCreatedAtDescPlaceIdDesc(me.getUserId()))
                .extracting(Place::getName, Place::getImageUrl)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("라멘", ARCHIVED),
                        org.assertj.core.groups.Tuple.tuple("우동", ARCHIVED),
                        org.assertj.core.groups.Tuple.tuple("카레", YOUTUBE));
        verify(archiver, times(1)).archive(INSTAGRAM);
    }

    @Test
    @DisplayName("형식이 틀린 카드가 섞여 한 장도 저장하지 않을 때는 사진도 옮기지 않는다.")
    void createPlaces_invalidRequest_doesNotArchive() throws Exception {
        // given
        var valid = PlaceTestFixtures.foodCard("라멘"); valid.put("imageUrl", INSTAGRAM);
        var invalid = PlaceTestFixtures.foodCard("우동"); invalid.remove("address");
        // when
        mockMvc.perform(post("/places").header("Authorization", "Bearer " + jwtProvider.createAccessToken(me.getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("places", List.of(valid, invalid)))))
                .andExpect(status().isBadRequest());
        // then
        verify(archiver, never()).archive(anyString());
    }

    @Test
    @DisplayName("이미 저장된 카드의 만료되는 사진을 한 번에 옮기고, 옮기지 못한 사진과 만료 없는 사진은 그대로 둔다.")
    void backfill_replacesStoredExpiringImages() {
        // given
        String failing = "https://scontent-ssn1-1.cdninstagram.com/v/t51/2_n.jpg?oe=1";
        Place a = save("라멘", INSTAGRAM);
        Place b = save("우동", INSTAGRAM);
        Place c = save("카레", failing);
        Place d = save("소바", YOUTUBE);
        em.flush();
        em.clear();
        // when
        int moved = backfill.run();
        em.clear();
        // then
        assertThat(moved).isEqualTo(1);
        assertThat(placeRepository.findById(a.getPlaceId()).orElseThrow().getImageUrl()).isEqualTo(ARCHIVED);
        assertThat(placeRepository.findById(b.getPlaceId()).orElseThrow().getImageUrl()).isEqualTo(ARCHIVED);
        assertThat(placeRepository.findById(c.getPlaceId()).orElseThrow().getImageUrl()).isEqualTo(failing);
        assertThat(placeRepository.findById(d.getPlaceId()).orElseThrow().getImageUrl()).isEqualTo(YOUTUBE);
        verify(archiver, times(1)).archive(INSTAGRAM);
        verify(archiver, never()).archive(YOUTUBE);
    }

    private Place save(String name, String imageUrl) {
        return placeRepository.save(Place.create(me, null, new PlaceContent(Category.FOOD, name, "福岡市博多区博多駅東3-1-6",
                new BigDecimal("33.5889000"), new BigDecimal("130.4245000"), "ChIJ-test", "후쿠오카",
                imageUrl, null, null, List.of("돈코츠 라멘"), "¥960~980",
                6500, "11:00-24:00", "하카타역 도보 6분", null,
                null, null, null, null, null, null, null)));
    }
}
