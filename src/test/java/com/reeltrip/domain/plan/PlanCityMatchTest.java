package com.reeltrip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.ai.PlanDraftClient;
import com.reeltrip.infra.ai.PlanDraftRequest;
import com.reeltrip.infra.ai.PlanDraftResult;
import com.reeltrip.support.TestFixtures;

/**
 * S4-2 AI 초안·S4-7 팁: 계획 도시는 도시 검색 결과("오사카시"), 카드 도시는 AI가 뽑은 이름("오사카")이라
 * 글자가 달라도 같은 도시로 봐야 해요 (#97)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanCityMatchTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlanRepository planRepository;
    @MockitoBean PlanDraftClient planDraftClient;

    User owner;
    Place food;
    Place tip;
    Plan plan;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("city-match@test.com"));
        // 카드 도시는 "후쿠오카" (PlanEditFixtures 기본값)
        food = placeRepository.save(PlanEditFixtures.place(owner, Category.FOOD, "이치란"));
        tip = placeRepository.save(PlanEditFixtures.place(owner, Category.TIP, "지하철 1일권"));
        placeRepository.save(Place.create(owner, null, new PlaceContent(Category.FOOD, "다른 도시 가게", "大阪市",
                new BigDecimal("34.6687"), new BigDecimal("135.5013"), null, "오사카", "https://storage.googleapis.com/test.jpg",
                null, null, List.of(), null, null, null, null, null, null, null, null, null, null, null, null)));
        // 계획 도시는 도시 검색에서 고른 "후쿠오카시"
        plan = Plan.create(owner, "후쿠오카 여행", TestFixtures.date(11, 14), TestFixtures.date(11, 14), "후쿠오카시",
                null, null, null);
        plan.addMember(owner, PlanRole.OWNER);
        plan.addDay(1, TestFixtures.date(11, 14));
        plan = planRepository.save(plan);
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(owner.getUserId());
    }

    @Test
    @DisplayName("계획 도시가 '후쿠오카시'여도 도시가 '후쿠오카'인 카드로 AI 초안을 만든다.")
    void aiDraft_cityWithSuffix_usesCardsOfSameCity() throws Exception {
        // given
        given(planDraftClient.draftPlan(any())).willReturn(new PlanDraftResult(List.of(), List.of(List.of(food.getPlaceId()))));
        // when
        mockMvc.perform(post("/plans/{planId}/ai-drafts", plan.getPlanId()).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].nodes[0].placeId").value(food.getPlaceId()))
                // then: 팁도 같은 도시로 보고 내려요
                .andExpect(jsonPath("$.tips[0].placeId").value(tip.getPlaceId()));
        // then: 다른 도시(오사카) 카드는 후보에 넣지 않아요
        var captor = ArgumentCaptor.forClass(PlanDraftRequest.class);
        verify(planDraftClient).draftPlan(captor.capture());
        assertThat(captor.getValue().candidates()).extracting(PlanDraftRequest.Candidate::placeId)
                .containsExactly(food.getPlaceId());
    }

    @Test
    @DisplayName("계획 보기의 팁도 도시 이름 끝의 '시'와 상관없이 같은 도시 카드를 보여준다.")
    void planDetail_cityWithSuffix_showsTips() throws Exception {
        mockMvc.perform(get("/plans/{planId}", plan.getPlanId()).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tips[0].placeId").value(tip.getPlaceId()));
    }
}
