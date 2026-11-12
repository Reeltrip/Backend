package com.reeltrip.domain.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.ShareType;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired EntityManager em;

    User owner;
    User stranger;
    Plan plan;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("plan-api-owner@test.com"));
        stranger = userRepository.save(TestFixtures.user("plan-api-stranger@test.com"));
        plan = Plan.create(owner, "후쿠오카 1박 2일", TestFixtures.date(11, 14), TestFixtures.date(11, 15),
                "후쿠오카", null, null, null);
        plan.addMember(owner, PlanRole.OWNER);
        plan.addDay(1, TestFixtures.date(11, 14));
        plan.addDay(2, TestFixtures.date(11, 15));
        planRepository.save(plan);
    }

    @Test
    @DisplayName("계획을 만들면 201과 함께 계획 id, 작성 중 상태, 일자 수, 불러온 카드 수를 받는다.")
    void createPlan_created() throws Exception {
        // given
        String body = """
                {"name":"오사카 3박 4일","startDate":"2026-11-14","endDate":"2026-11-17","city":"오사카",
                 "cityPlaceId":"ChIJ-osaka","latitude":34.6937,"longitude":135.5023}
                """;
        // when / then
        mockMvc.perform(post("/plans").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").isNumber())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalDayCount").value(4))
                .andExpect(jsonPath("$.loadedPlaceCount").value(0));
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦은 계획을 만들면 400 INVALID_PLAN_PERIOD를 받는다.")
    void createPlan_invalidPeriod() throws Exception {
        // given
        String body = """
                {"name":"오사카","startDate":"2026-11-17","endDate":"2026-11-14","city":"오사카"}
                """;
        // when / then
        mockMvc.perform(post("/plans").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_PERIOD"))
                .andExpect(jsonPath("$.message").value("여행 기간을 다시 확인해주세요"));
    }

    @Test
    @DisplayName("여행명이 빠진 계획을 만들면 400 INVALID_INPUT을 받는다.")
    void createPlan_missingName() throws Exception {
        // given
        String body = """
                {"startDate":"2026-11-14","endDate":"2026-11-17","city":"오사카"}
                """;
        // when / then
        mockMvc.perform(post("/plans").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("로그인하지 않고 계획을 만들면 401을 받는다.")
    void createPlan_withoutLogin() throws Exception {
        // given / when / then
        mockMvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("계획 목록은 plans와 totalCount를 내려주고, 정해지지 않은 owner 값은 400 INVALID_INPUT으로 막는다.")
    void getPlans() throws Exception {
        // given: setUp의 내 계획 1개
        // when / then
        mockMvc.perform(get("/plans").param("owner", "me").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.plans[0].planId").value(plan.getPlanId()))
                .andExpect(jsonPath("$.plans[0].totalDayCount").value(2))
                .andExpect(jsonPath("$.plans[0].ownerNickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.plans[0].members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.plans[0].coverImageUrl").value(nullValue()));
        mockMvc.perform(get("/plans").param("owner", "everyone").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/plans").param("status", "WRONG").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("공유를 켜지 않은 계획을 로그인하지 않고 보면 404 PLAN_NOT_FOUND를 받는다.")
    void getPlan_notShared_anonymous_notFound() throws Exception {
        // given: 공유 꺼진 계획
        // when / then
        mockMvc.perform(get("/plans/{planId}", plan.getPlanId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("읽기 전용으로 공유한 계획은 로그인하지 않아도 일자·노드·팁과 함께 볼 수 있고 role은 null이다.")
    void getPlan_readOnlyShared_anonymous_ok() throws Exception {
        // given: 읽기 전용 공유, 1일차에 노드 하나, 같은 도시 팁 카드 하나
        Place ramen = placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("하카타 잇소", "후쿠오카")));
        plan.getDays().get(0).addNode(ramen, 0);
        plan.getDays().get(0).changeCompleted(true);
        plan.changeShareType(ShareType.READ_ONLY);
        em.flush();
        em.clear();
        // when / then
        mockMvc.perform(get("/plans/{planId}", plan.getPlanId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(plan.getPlanId()))
                .andExpect(jsonPath("$.role").value(nullValue()))
                .andExpect(jsonPath("$.startDate").value("2026-11-14"))
                .andExpect(jsonPath("$.days[0].dayNumber").value(1))
                .andExpect(jsonPath("$.days[0].isCompleted").value(true))
                .andExpect(jsonPath("$.days[0].nodes[0].placeId").value(ramen.getPlaceId()))
                .andExpect(jsonPath("$.days[0].nodes[0].category").value("FOOD"))
                .andExpect(jsonPath("$.days[0].nodes[0].latitude").value(33.5889))
                .andExpect(jsonPath("$.days[0].nodes[0].alternatives").isArray())
                .andExpect(jsonPath("$.stays").isArray())
                .andExpect(jsonPath("$.tips").isArray());
    }

    @Test
    @DisplayName("내 계획을 로그인해서 보면 role이 OWNER다.")
    void getPlan_owner() throws Exception {
        // given / when / then
        mockMvc.perform(get("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    @DisplayName("남의 비공유 계획을 수정하거나 삭제하면 404 PLAN_NOT_FOUND를 받는다.")
    void modifyAndRemove_stranger_notFound() throws Exception {
        // given: 공유 꺼진 남의 계획
        // when / then
        mockMvc.perform(patch("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"바꿈\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        mockMvc.perform(delete("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("읽기 전용으로 공유받은 멤버가 계획을 수정하면 403 PLAN_FORBIDDEN을 받는다.")
    void modifyPlan_viewer_forbidden() throws Exception {
        // given: VIEWER 멤버
        plan.addMember(stranger, PlanRole.VIEWER);
        plan.changeShareType(ShareType.READ_ONLY);
        em.flush();
        // when / then
        mockMvc.perform(patch("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"바꿈\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
    }

    @Test
    @DisplayName("편집 멤버는 계획을 수정할 수 있지만 삭제하면 403 PLAN_FORBIDDEN을 받는다.")
    void editor_modifyOk_removeForbidden() throws Exception {
        // given: EDITOR 멤버
        plan.addMember(stranger, PlanRole.EDITOR);
        plan.changeShareType(ShareType.EDIT);
        em.flush();
        // when / then
        mockMvc.perform(patch("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"후쿠오카 맛집 투어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("후쿠오카 맛집 투어"))
                .andExpect(jsonPath("$.role").value("EDITOR"));
        mockMvc.perform(delete("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
    }

    @Test
    @DisplayName("기간을 거꾸로 바꾸면 400 INVALID_PLAN_PERIOD를 받는다.")
    void modifyPlan_invalidPeriod() throws Exception {
        // given / when / then
        mockMvc.perform(patch("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-11-30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_PERIOD"));
    }

    @Test
    @DisplayName("OWNER가 계획을 지우면 204를 받고 계획이 사라진다.")
    void removePlan_owner_noContent() throws Exception {
        // given / when
        mockMvc.perform(delete("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();
        // then
        assertThat(planRepository.findById(plan.getPlanId())).isEmpty();
    }

    @Test
    @DisplayName("읽기 전용 공유 계획을 복제하면 201과 새 계획 id를 받고, 그 계획은 부른 사람의 것이다.")
    void copyPlan_created() throws Exception {
        // given: 읽기 전용 공유
        plan.changeShareType(ShareType.READ_ONLY);
        em.flush();
        // when
        String response = mockMvc.perform(post("/plans/{planId}/copies", plan.getPlanId())
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").isNumber())
                .andReturn().getResponse().getContentAsString();
        // then
        long copyId = Long.parseLong(response.replaceAll("\\D", ""));
        assertThat(copyId).isNotEqualTo(plan.getPlanId());
        assertThat(planRepository.findById(copyId).orElseThrow().isOwnedBy(stranger.getUserId())).isTrue();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getUserId());
    }
}
