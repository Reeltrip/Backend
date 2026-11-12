package com.reeltrip.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.ShareType;
import com.reeltrip.domain.plan.repository.PlanMemberRepository;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class PlanAccessServiceTest {

    @Autowired PlanAccessService planAccessService;
    @Autowired PlanRepository planRepository;
    @Autowired PlanMemberRepository planMemberRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    User owner;
    User stranger;
    Plan plan;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("owner@test.com"));
        stranger = userRepository.save(TestFixtures.user("stranger@test.com"));
        plan = Plan.create(owner, "후쿠오카 2박 3일", TestFixtures.date(11, 14), TestFixtures.date(11, 16),
                "후쿠오카", null, null, null);
        plan.addMember(owner, PlanRole.OWNER);
        planRepository.save(plan);
    }

    @Test
    @DisplayName("멤버가 아니고 공유도 꺼진 계획은 있다는 것도 알리지 않고 404로 실패한다.")
    void read_notShared_stranger_notFound() {
        // given: 공유를 켜지 않은 계획
        // when / then: 남이 열면 PLAN_NOT_FOUND
        assertThatThrownBy(() -> planAccessService.getReadablePlan(plan.getPlanId(), stranger.getUserId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("공유를 켜지 않은 계획은 로그인하지 않으면 볼 수 없다.")
    void read_notShared_anonymous_notFound() {
        // given: 공유를 켜지 않은 계획
        // when / then: 비로그인이 열면 PLAN_NOT_FOUND
        assertThatThrownBy(() -> planAccessService.getReadablePlan(plan.getPlanId(), null))
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("읽기 전용으로 공유한 계획은 로그인하지 않아도 볼 수 있다.")
    void read_readOnlyShared_anonymous_ok() {
        // given: 읽기 전용 공유
        plan.changeShareType(ShareType.READ_ONLY);
        // when
        PlanAccess access = planAccessService.getReadablePlan(plan.getPlanId(), null);
        // then: 역할 없이 읽기만
        assertThat(access.plan().getPlanId()).isEqualTo(plan.getPlanId());
        assertThat(access.role()).isNull();
    }

    @Test
    @DisplayName("함께 편집으로 공유한 계획을 로그인 사용자가 열면 편집 멤버로 추가된다.")
    void read_editShared_loginUser_joinsAsEditor() {
        // given: 함께 편집 공유
        plan.changeShareType(ShareType.EDIT);
        // when: 멤버가 아닌 사람이 열면
        PlanAccess access = planAccessService.getReadablePlan(plan.getPlanId(), stranger.getUserId());
        em.flush();
        // then: EDITOR로 들어감
        assertThat(access.role()).isEqualTo(PlanRole.EDITOR);
        assertThat(planMemberRepository.findByPlan_PlanIdAndUser_UserId(plan.getPlanId(), stranger.getUserId()))
                .get().extracting("role").isEqualTo(PlanRole.EDITOR);
    }

    @Test
    @DisplayName("읽기 전용으로 공유받은 멤버가 계획을 고치면 권한 없음(403)으로 실패한다.")
    void edit_viewer_forbidden() {
        // given: 읽기 전용 공유를 열어 VIEWER가 된 사람
        plan.changeShareType(ShareType.READ_ONLY);
        planAccessService.getReadablePlan(plan.getPlanId(), stranger.getUserId());
        em.flush();
        // when / then
        assertThatThrownBy(() -> planAccessService.getEditablePlan(plan.getPlanId(), stranger.getUserId()))
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_FORBIDDEN);
    }

    @Test
    @DisplayName("편집 멤버도 계획 삭제·공유 링크 발급은 할 수 없다.")
    void own_editor_forbidden() {
        // given: EDITOR
        plan.addMember(stranger, PlanRole.EDITOR);
        em.flush();
        // when / then
        assertThat(planAccessService.getEditablePlan(plan.getPlanId(), stranger.getUserId()).role())
                .isEqualTo(PlanRole.EDITOR);
        assertThatThrownBy(() -> planAccessService.getOwnedPlan(plan.getPlanId(), stranger.getUserId()))
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_FORBIDDEN);
    }

    @Test
    @DisplayName("일자·구역·노드·플랜B·숙소를 붙인 계획을 저장하고 지우면 딸린 행이 모두 같이 지워진다.")
    void entityGraph_saveAndCascadeDelete() {
        // given: 카드 두 장과 일자·노드·플랜B·숙소가 있는 계획
        Place ramen = placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("하카타 잇소", "후쿠오카")));
        Place other = placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("이치란", "후쿠오카")));
        PlanDay day = plan.addDay(1, TestFixtures.date(11, 14));
        day.addArea("하카타", null, null);
        day.addNode(ramen, 0).addAlternative(other);
        plan.addStay(other);
        em.flush();
        em.clear();
        // when: 계획을 지우면
        planRepository.deleteById(plan.getPlanId());
        em.flush();
        // then: 계획 쪽 행은 모두 지워지고 카드는 남음
        Long left = em.createQuery("select count(n) from PlanNode n", Long.class).getSingleResult()
                + em.createQuery("select count(a) from PlanAlternative a", Long.class).getSingleResult()
                + em.createQuery("select count(d) from PlanDay d", Long.class).getSingleResult()
                + em.createQuery("select count(s) from PlanStay s", Long.class).getSingleResult();
        assertThat(left).isZero();
        assertThat(placeRepository.count()).isEqualTo(2);
    }
}
