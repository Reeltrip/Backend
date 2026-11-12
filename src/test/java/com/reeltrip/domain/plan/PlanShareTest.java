package com.reeltrip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.plan.dto.ShareLinkCreateRequest;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.ShareType;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.plan.service.PlanAccess;
import com.reeltrip.domain.plan.service.PlanAccessService;
import com.reeltrip.domain.plan.service.PlanShareService;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** POST /plans/{planId}/share-links (S4-8 공유) */
@SpringBootTest(properties = "app.share.base-url=https://wayvi.test/")
@AutoConfigureMockMvc
@Transactional
class PlanShareTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired PlanShareService planShareService;
    @Autowired PlanAccessService planAccessService;
    @Autowired EntityManager em;

    User owner;
    User editor;
    User stranger;
    Plan plan;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("share-owner@test.com"));
        editor = userRepository.save(TestFixtures.user("share-editor@test.com"));
        stranger = userRepository.save(TestFixtures.user("share-stranger@test.com"));
        plan = PlanEditFixtures.plan(owner, 2);
        plan.addMember(editor, PlanRole.EDITOR);
        planRepository.save(plan);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("계획 주인이 공유 링크를 만들면 201과 서비스 주소/plan/{planId} 링크를 받고 계획 공유가 켜진다.")
    void createShareLink_owner_created() throws Exception {
        // given: 공유가 꺼진 계획
        // when
        createShareLink(owner, "READ_ONLY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareUrl").value("https://wayvi.test/plan/" + plan.getPlanId()))
                .andExpect(jsonPath("$.shareType").value("READ_ONLY"));
        // then (테스트 트랜잭션이라 커밋 대신 flush)
        em.flush();
        em.clear();
        assertThat(planRepository.findById(plan.getPlanId()).orElseThrow().getShareType())
                .isEqualTo(ShareType.READ_ONLY);

        // when: 함께 편집으로 다시 만들면 종류만 바뀜
        createShareLink(owner, "EDIT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareType").value("EDIT"));
    }

    @Test
    @DisplayName("공유 링크는 주인만 만들 수 있어서 편집 멤버는 403, 멤버가 아닌 사람은 404로 실패한다.")
    void createShareLink_notOwner() throws Exception {
        // given / when / then
        createShareLink(editor, "EDIT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
        createShareLink(stranger, "EDIT")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("공유 종류가 READ_ONLY·EDIT가 아니거나 빠지면 입력 오류로 실패한다.")
    void createShareLink_invalidType() throws Exception {
        // given / when / then
        createShareLink(owner, "PUBLIC")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/plans/{planId}/share-links", plan.getPlanId())
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(owner.getUserId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("로그인하지 않으면 공유 링크를 만들 수 없다.")
    void createShareLink_anonymous_unauthorized() throws Exception {
        // given / when / then
        mockMvc.perform(post("/plans/{planId}/share-links", plan.getPlanId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareType\":\"READ_ONLY\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("공유를 켜지 않은 계획은 비회원이 볼 수 없고, 공유 링크를 만든 뒤에는 비회원도 읽을 수 있다.")
    void anonymousRead_onlyAfterSharing() {
        // given: 공유가 꺼진 계획
        // when / then: 비회원 조회는 404
        assertThatThrownBy(() -> planAccessService.getReadablePlan(plan.getPlanId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_NOT_FOUND);

        // when: 주인이 읽기 전용 링크를 만들면
        planShareService.createShareLink(plan.getPlanId(), owner.getUserId(),
                new ShareLinkCreateRequest(ShareType.READ_ONLY));
        em.flush();
        em.clear();

        // then: 비회원도 역할 없이 읽을 수 있음
        PlanAccess access = planAccessService.getReadablePlan(plan.getPlanId(), null);
        assertThat(access.plan().getPlanId()).isEqualTo(plan.getPlanId());
        assertThat(access.role()).isNull();
    }

    private ResultActions createShareLink(User user, String shareType) throws Exception {
        return mockMvc.perform(post("/plans/{planId}/share-links", plan.getPlanId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shareType\":\"" + shareType + "\"}"));
    }
}
