package com.reeltrip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.ai.PlanDraftClient;
import com.reeltrip.infra.ai.PlanDraftRequest;
import com.reeltrip.infra.ai.PlanDraftResult;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** S4-2 AI 초안. LLM은 가짜(PlanDraftClient 목)로 바꿔 끼워요. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanDraftServiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlanRepository planRepository;
    @Autowired EntityManager em;
    @MockitoBean PlanDraftClient planDraftClient;

    User owner;
    Plan plan;
    Place food;
    Place cafe;
    Place sight;
    Place stay;
    Place tip;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("draft-owner@test.com"));
        food = placeRepository.save(PlanEditFixtures.place(owner, Category.FOOD, "이치란"));
        cafe = placeRepository.save(PlanEditFixtures.place(owner, Category.CAFE, "릴로 커피"));
        sight = placeRepository.save(PlanEditFixtures.place(owner, Category.SIGHTSEEING, "오호리 공원"));
        stay = placeRepository.save(PlanEditFixtures.place(owner, Category.STAY, "호텔"));
        tip = placeRepository.save(PlanEditFixtures.place(owner, Category.TIP, "지하철 1일권"));
        plan = planRepository.save(PlanEditFixtures.plan(owner, 2));
    }

    private String token(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getUserId());
    }

    private PlanDay day(int dayNumber) {
        return plan.getDays().stream().filter(d -> d.getDayNumber() == dayNumber).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("AI 초안을 만들면 숙소와 일자별 노드가 AI가 정한 순서대로 채워지고 계획은 작성 중으로 남는다.")
    void createAiDraft_fillsStaysAndNodes() throws Exception {
        // given: AI가 1일차 [관광지, 맛집], 2일차 [카페], 숙소 [호텔]로 짬
        given(planDraftClient.draftPlan(any())).willReturn(new PlanDraftResult(
                List.of(stay.getPlaceId()),
                List.of(List.of(sight.getPlaceId(), food.getPlaceId()), List.of(cafe.getPlaceId()))));

        // when
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()).header("Authorization", token(owner)))
                // then: 응답은 계획 보기와 같은 모양
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stays[0].placeId").value(stay.getPlaceId()))
                .andExpect(jsonPath("$.days[0].nodes[0].placeId").value(sight.getPlaceId()))
                .andExpect(jsonPath("$.days[0].nodes[0].orderIndex").value(0))
                .andExpect(jsonPath("$.days[0].nodes[1].placeId").value(food.getPlaceId()))
                .andExpect(jsonPath("$.days[1].nodes[0].placeId").value(cafe.getPlaceId()))
                .andExpect(jsonPath("$.days[0].isCompleted").value(false));
    }

    @Test
    @DisplayName("AI에는 계획 도시의 장소 카드만 후보로 넘기고 교통·팁 카드는 넘기지 않는다.")
    void createAiDraft_candidatesArePlaceCardsOnly() throws Exception {
        // given
        given(planDraftClient.draftPlan(any())).willReturn(new PlanDraftResult(List.of(), List.of(List.of(), List.of())));
        // when
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()).header("Authorization", token(owner)))
                .andExpect(status().isOk());
        // then
        var captor = org.mockito.ArgumentCaptor.forClass(PlanDraftRequest.class);
        verify(planDraftClient).draftPlan(captor.capture());
        assertThat(captor.getValue().dayCount()).isEqualTo(2);
        assertThat(captor.getValue().candidates()).extracting(PlanDraftRequest.Candidate::placeId)
                .containsExactlyInAnyOrder(food.getPlaceId(), cafe.getPlaceId(), sight.getPlaceId(), stay.getPlaceId())
                .doesNotContain(tip.getPlaceId());
    }

    @Test
    @DisplayName("이미 짠 동선이 있어도 AI 초안이 덮어쓰고, 같은 장소를 다시 넣어도 저장된다.")
    void createAiDraft_overwritesExistingNodes() throws Exception {
        // given: 1일차 맛집, 2일차 카페, 숙소가 있고 1일차가 완성 표시된 계획
        day(1).addNode(food, 0);
        day(2).addNode(cafe, 0);
        day(1).changeCompleted(true);
        plan.addStay(stay);
        em.flush();
        given(planDraftClient.draftPlan(any())).willReturn(new PlanDraftResult(
                List.of(stay.getPlaceId()),
                List.of(List.of(cafe.getPlaceId()), List.of(food.getPlaceId(), sight.getPlaceId()))));

        // when
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()).header("Authorization", token(owner)))
                .andExpect(status().isOk())
                // then: 예전 동선은 사라지고 AI 초안으로 바뀜
                .andExpect(jsonPath("$.days[0].nodes.length()").value(1))
                .andExpect(jsonPath("$.days[0].nodes[0].placeId").value(cafe.getPlaceId()))
                .andExpect(jsonPath("$.days[1].nodes[1].placeId").value(sight.getPlaceId()))
                .andExpect(jsonPath("$.days[0].isCompleted").value(false))
                .andExpect(jsonPath("$.stays.length()").value(1));
    }

    @Test
    @DisplayName("보관함에 계획 도시의 장소 카드가 없으면 AI를 부르지 않고 400 AI_DRAFT_NO_PLACES로 실패한다.")
    void createAiDraft_noPlaces() throws Exception {
        // given: 다른 회원의 계획 (그 회원 보관함은 비어 있음)
        User other = userRepository.save(TestFixtures.user("draft-empty@test.com"));
        Plan emptyPlan = planRepository.save(PlanEditFixtures.plan(other, 1));
        // when / then
        mockMvc.perform(post("/plans/{planId}/ai-drafts", emptyPlan.getPlanId()).header("Authorization", token(other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_DRAFT_NO_PLACES"));
        verify(planDraftClient, never()).draftPlan(any());
    }

    @Test
    @DisplayName("AI 호출이 실패하면 500 AI_DRAFT_FAILED로 실패하고 기존 동선은 그대로 남는다.")
    void createAiDraft_aiFails_keepsPlan() throws Exception {
        // given: 1일차에 맛집이 있는 계획
        day(1).addNode(food, 0);
        em.flush();
        given(planDraftClient.draftPlan(any())).willThrow(new BusinessException(ErrorCode.AI_DRAFT_FAILED));
        // when
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()).header("Authorization", token(owner)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("AI_DRAFT_FAILED"));
        // then
        em.clear();
        Plan reloaded = planRepository.findById(plan.getPlanId()).orElseThrow();
        assertThat(reloaded.getDays().get(0).getNodes()).extracting(n -> n.getPlace().getPlaceId())
                .containsExactly(food.getPlaceId());
        assertThat(reloaded.getStatus()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("읽기 전용 멤버가 AI 초안을 만들면 403, 남의 계획은 404, 로그인 안 하면 401로 실패한다.")
    void createAiDraft_permissions() throws Exception {
        // given
        User viewer = userRepository.save(TestFixtures.user("draft-viewer@test.com"));
        User stranger = userRepository.save(TestFixtures.user("draft-stranger@test.com"));
        plan.addMember(viewer, PlanRole.VIEWER);
        em.flush();
        // when / then
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()).header("Authorization", token(viewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()).header("Authorization", token(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()))
                .andExpect(status().isUnauthorized());
        verify(planDraftClient, never()).draftPlan(any());
    }
}
