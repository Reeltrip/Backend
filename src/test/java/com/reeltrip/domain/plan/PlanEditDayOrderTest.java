package com.reeltrip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.repository.PlanDayRepository;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** PATCH /plans/{planId}/days/order (S4-7 일자 순서 바꾸기) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanEditDayOrderTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlanRepository planRepository;
    @Autowired PlanDayRepository planDayRepository;
    @Autowired EntityManager em;

    User owner;
    User viewer;
    User stranger;
    Plan plan;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("order-owner@test.com"));
        viewer = userRepository.save(TestFixtures.user("order-viewer@test.com"));
        stranger = userRepository.save(TestFixtures.user("order-stranger@test.com"));
        plan = PlanEditFixtures.plan(owner, 3);
        plan.addMember(viewer, PlanRole.VIEWER);
        planRepository.save(plan);
        em.flush();
    }

    @Test
    @DisplayName("일자 순서를 바꾸면 새 순서대로 일차와 날짜가 다시 매겨지고 노드·구역·완성 여부는 원래 일자를 따라간다.")
    void reorderDays_daysFollow() throws Exception {
        // given: 2일차에 라멘 노드·구역·완성 표시
        Place ramen = placeRepository.save(PlanEditFixtures.place(owner, Category.FOOD, "하카타 잇소"));
        PlanDay day2 = plan.getDays().get(1);
        day2.addNode(ramen, 0);
        day2.addArea("하카타", null, null);
        day2.changeCompleted(true);
        em.flush();
        Long day2Id = day2.getPlanDayId();
        em.clear();

        // when: [2, 1, 3]으로 바꾸면
        reorder(owner, "[2,1,3]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].planDayId").value(day2Id))
                .andExpect(jsonPath("$.days[0].dayNumber").value(1))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-14"))
                .andExpect(jsonPath("$.days[1].dayNumber").value(2))
                .andExpect(jsonPath("$.days[1].date").value("2026-11-15"));

        // then: 옛 2일차가 새 1일차가 되고 노드·구역·완성 여부가 따라옴
        em.clear();
        PlanDay newDay1 = planDayRepository.findByPlan_PlanIdAndDayNumber(plan.getPlanId(), 1).orElseThrow();
        assertThat(newDay1.getPlanDayId()).isEqualTo(day2Id);
        assertThat(newDay1.getDate()).isEqualTo(TestFixtures.date(11, 14));
        assertThat(newDay1.isCompleted()).isTrue();
        assertThat(newDay1.getNodes()).extracting(node -> node.getPlace().getPlaceId())
                .containsExactly(ramen.getPlaceId());
        assertThat(newDay1.getAreas()).extracting("name").containsExactly("하카타");
        PlanDay newDay2 = planDayRepository.findByPlan_PlanIdAndDayNumber(plan.getPlanId(), 2).orElseThrow();
        assertThat(newDay2.getNodes()).isEmpty();
        assertThat(newDay2.getDate()).isEqualTo(TestFixtures.date(11, 15));
    }

    @Test
    @DisplayName("일자 번호가 1부터 n까지 한 번씩 나오지 않으면 입력 오류로 실패한다.")
    void reorderDays_invalidPermutation() throws Exception {
        // given: 3일짜리 계획
        em.clear();
        // when / then: 중복, 개수 부족, 범위 밖
        for (String dayNumbers : new String[] {"[1,1,3]", "[2,1]", "[1,2,4]", "[]"}) {
            reorder(owner, dayNumbers)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    @DisplayName("읽기 전용 멤버가 일자 순서를 바꾸면 403, 멤버가 아닌 사람이 바꾸면 404로 실패한다.")
    void reorderDays_permission() throws Exception {
        // given: VIEWER와 멤버가 아닌 사람
        em.clear();
        // when / then
        reorder(viewer, "[2,1,3]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
        reorder(stranger, "[2,1,3]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    private ResultActions reorder(User user, String dayNumbersJson) throws Exception {
        return mockMvc.perform(patch("/plans/{planId}/days/order", plan.getPlanId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayNumbers\":" + dayNumbersJson + "}"));
    }
}
