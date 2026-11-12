package com.reeltrip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStay;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** PUT /plans/{planId}/stays (S4-3), PUT /plans/{planId}/days/{dayNumber}/areas (S4-4) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanEditStaysAndAreasTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlanRepository planRepository;
    @Autowired EntityManager em;

    User owner;
    User viewer;
    User stranger;
    Place hotel;
    Place hostel;
    Place ramen;
    Place tip;
    Plan plan;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("stay-owner@test.com"));
        viewer = userRepository.save(TestFixtures.user("stay-viewer@test.com"));
        stranger = userRepository.save(TestFixtures.user("stay-stranger@test.com"));
        hotel = placeRepository.save(PlanEditFixtures.place(owner, Category.STAY, "오리엔탈 호텔"));
        hostel = placeRepository.save(PlanEditFixtures.place(owner, Category.STAY, "하카타 호스텔"));
        ramen = placeRepository.save(PlanEditFixtures.place(owner, Category.FOOD, "하카타 잇소"));
        tip = placeRepository.save(PlanEditFixtures.place(owner, Category.TIP, "지하철 1일권"));
        plan = PlanEditFixtures.plan(owner, 2);
        plan.addMember(viewer, PlanRole.VIEWER);
        planRepository.save(plan);
        em.flush();
    }

    @Test
    @DisplayName("숙소를 저장하면 고른 숙소 목록으로 통째로 바뀌고, 남는 숙소는 그대로 유지된다.")
    void replaceStays_replacesWholeList() throws Exception {
        // given: 오리엔탈 호텔이 이미 숙소로 있음
        PlanStay kept = plan.addStay(hotel);
        em.flush();
        em.clear();

        // when: 호텔·호스텔로 저장하면
        replaceStays(owner, "[" + hotel.getPlaceId() + "," + hostel.getPlaceId() + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays.length()").value(2))
                .andExpect(jsonPath("$.stays[0].planStayId").value(kept.getPlanStayId()))
                .andExpect(jsonPath("$.stays[0].placeId").value(hotel.getPlaceId()))
                .andExpect(jsonPath("$.stays[0].name").value("오리엔탈 호텔"))
                .andExpect(jsonPath("$.stays[0].latitude").value(33.5889))
                .andExpect(jsonPath("$.stays[1].placeId").value(hostel.getPlaceId()));

        // when: 호스텔만 남기면 호텔은 빠짐
        replaceStays(owner, "[" + hostel.getPlaceId() + "]")
                .andExpect(jsonPath("$.stays.length()").value(1))
                .andExpect(jsonPath("$.stays[0].placeId").value(hostel.getPlaceId()));

        // when: 빈 목록이면 숙소 없음
        replaceStays(owner, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays.length()").value(0));
        em.clear();
        assertThat(em.createQuery("select count(s) from PlanStay s", Long.class).getSingleResult()).isZero();
    }

    @Test
    @DisplayName("숙소에는 STAY 카드만 넣을 수 있고 맛집이나 교통·팁 카드는 400으로 실패한다.")
    void replaceStays_notStay_rejected() throws Exception {
        // given: 맛집 카드와 팁 카드
        em.clear();
        // when / then
        replaceStays(owner, "[" + ramen.getPlaceId() + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_PLACE"));
        replaceStays(owner, "[" + tip.getPlaceId() + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_PLACE"));
    }

    @Test
    @DisplayName("같은 숙소를 요청에 두 번 넣으면 입력 오류로 실패한다.")
    void replaceStays_duplicate_invalidInput() throws Exception {
        // given / when / then
        replaceStays(owner, "[" + hotel.getPlaceId() + "," + hotel.getPlaceId() + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("다른 사람의 숙소 카드나 없는 카드는 404 PLACE_NOT_FOUND로 실패한다.")
    void replaceStays_strangerPlace_notFound() throws Exception {
        // given: 계획 멤버가 아닌 사람의 숙소 카드
        Place strangerHotel = placeRepository.save(PlanEditFixtures.place(stranger, Category.STAY, "남의 호텔"));
        em.flush();
        em.clear();
        // when / then
        replaceStays(owner, "[" + strangerHotel.getPlaceId() + "]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        replaceStays(owner, "[999999]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("읽기 전용 멤버가 숙소를 바꾸면 403, 멤버가 아닌 사람이 바꾸면 404로 실패한다.")
    void replaceStays_permission() throws Exception {
        // given: VIEWER와 멤버가 아닌 사람
        em.clear();
        // when / then
        replaceStays(viewer, "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
        replaceStays(stranger, "[]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("구역을 저장하면 그 날의 구역이 요청한 이름·좌표 목록으로 통째로 바뀐다.")
    void replaceAreas_replacesWholeList() throws Exception {
        // given: 1일차에 이미 구역 하나
        plan.getDays().get(0).addArea("텐진", null, null);
        em.flush();
        em.clear();

        // when
        replaceAreas(owner, 1, "[{\"name\":\"하카타\",\"latitude\":33.5902,\"longitude\":130.4207},"
                + "{\"name\":\"나카스\",\"latitude\":null,\"longitude\":null}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.areas.length()").value(2))
                .andExpect(jsonPath("$.areas[0].name").value("하카타"))
                .andExpect(jsonPath("$.areas[0].latitude").value(33.5902))
                .andExpect(jsonPath("$.areas[1].name").value("나카스"));

        // then: 이전 구역은 없어지고 순서대로 저장됨 (테스트 트랜잭션이라 커밋 대신 flush)
        em.flush();
        em.clear();
        List<String> names = em.createQuery(
                        "select a.name from PlanDayArea a order by a.orderIndex", String.class)
                .getResultList();
        assertThat(names).containsExactly("하카타", "나카스");
    }

    @Test
    @DisplayName("구역 이름이 비었거나 없는 일자면 실패한다.")
    void replaceAreas_invalid() throws Exception {
        // given / when / then: 이름 빈칸
        replaceAreas(owner, 1, "[{\"name\":\" \"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        // when / then: 없는 일자
        replaceAreas(owner, 5, "[]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_DAY_NOT_FOUND"));
        // when / then: VIEWER
        replaceAreas(viewer, 1, "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
    }

    private ResultActions replaceStays(User user, String placeIdsJson) throws Exception {
        return mockMvc.perform(put("/plans/{planId}/stays", plan.getPlanId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeIds\":" + placeIdsJson + "}"));
    }

    private ResultActions replaceAreas(User user, int dayNumber, String areasJson) throws Exception {
        return mockMvc.perform(put("/plans/{planId}/days/{dayNumber}/areas", plan.getPlanId(), dayNumber)
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areas\":" + areasJson + "}"));
    }
}
