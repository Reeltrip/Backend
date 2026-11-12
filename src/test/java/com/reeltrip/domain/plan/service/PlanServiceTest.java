package com.reeltrip.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.dto.PlanCreateRequest;
import com.reeltrip.domain.plan.dto.PlanCreateResponse;
import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.domain.plan.dto.PlanListRequest;
import com.reeltrip.domain.plan.dto.PlanListResponse;
import com.reeltrip.domain.plan.dto.PlanSummaryResponse;
import com.reeltrip.domain.plan.dto.PlanUpdateRequest;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.domain.plan.entity.ShareType;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class PlanServiceTest {

    @Autowired PlanService planService;
    @Autowired PlanRepository planRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    User owner;
    User friend;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("plan-owner@test.com"));
        friend = userRepository.save(TestFixtures.user("plan-friend@test.com"));
    }

    @Test
    @DisplayName("계획을 만들면 기간에 맞춰 1일차부터 마지막 날까지 일자가 날짜와 함께 만들어지고 만든 사람이 OWNER가 된다.")
    void createPlan_createsDaysByPeriod() {
        // given: 11/14 ~ 11/17 (3박 4일)
        PlanCreateRequest request = createRequest("오사카", TestFixtures.date(11, 14), TestFixtures.date(11, 17));
        // when
        PlanCreateResponse response = planService.createPlan(owner.getUserId(), request);
        em.flush();
        em.clear();
        // then
        Plan plan = planRepository.findById(response.planId()).orElseThrow();
        assertThat(response.totalDayCount()).isEqualTo(4);
        assertThat(response.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.getDays()).extracting(PlanDay::getDayNumber, PlanDay::getDate).containsExactly(
                tuple(1, TestFixtures.date(11, 14)), tuple(2, TestFixtures.date(11, 15)),
                tuple(3, TestFixtures.date(11, 16)), tuple(4, TestFixtures.date(11, 17)));
        assertThat(plan.getMembers()).extracting(m -> m.getUser().getUserId(), m -> m.getRole())
                .containsExactly(tuple(owner.getUserId(), PlanRole.OWNER));
    }

    @Test
    @DisplayName("당일치기(시작일과 종료일이 같음)도 1일차 하나로 만들어진다.")
    void createPlan_sameDay_oneDay() {
        // given / when
        PlanCreateResponse response = planService.createPlan(owner.getUserId(),
                createRequest("오사카", TestFixtures.date(11, 14), TestFixtures.date(11, 14)));
        // then
        assertThat(response.totalDayCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 여행 기간 오류로 실패한다.")
    void createPlan_invalidPeriod() {
        // given: 시작일 11/17, 종료일 11/14
        PlanCreateRequest request = createRequest("오사카", TestFixtures.date(11, 17), TestFixtures.date(11, 14));
        // when / then
        assertThatThrownBy(() -> planService.createPlan(owner.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PLAN_PERIOD);
    }

    @Test
    @DisplayName("계획을 만들면 내 보관함에서 같은 도시의 장소 카드만 센다(교통·팁, 다른 도시, 남의 카드는 빼고).")
    void createPlan_countsCityPlaceCards() {
        // given: 후쿠오카 장소 카드 2장 + 후쿠오카 팁 1장 + 오사카 카드 1장 + 남의 후쿠오카 카드 1장
        placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("하카타 잇소", "후쿠오카")));
        placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("이치란", "후쿠오카")));
        placeRepository.save(Place.create(owner, null, tipContent("지하철 1일권", "후쿠오카")));
        placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("킨류", "오사카")));
        placeRepository.save(Place.create(friend, null, TestFixtures.foodContent("우동 타이라", "후쿠오카")));
        // when
        PlanCreateResponse response = planService.createPlan(owner.getUserId(),
                createRequest("후쿠오카", TestFixtures.date(11, 14), TestFixtures.date(11, 16)));
        // then
        assertThat(response.loadedPlaceCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("그 도시의 장소 카드가 하나도 없어도 계획은 만들어진다.")
    void createPlan_noCards_stillCreated() {
        // given: 카드 없음
        // when
        PlanCreateResponse response = planService.createPlan(owner.getUserId(),
                createRequest("삿포로", TestFixtures.date(11, 14), TestFixtures.date(11, 15)));
        // then
        assertThat(response.loadedPlaceCount()).isZero();
        assertThat(planRepository.findById(response.planId())).isPresent();
    }

    @Test
    @DisplayName("계획 목록은 완성 일자 수·전체 일자 수·노드 수·첫 노드 사진·멤버를 담는다.")
    void getPlans_progressAndCover() {
        // given: 3일 계획에서 1일차 완성, 2일차에 노드 2개(순서 0번 카드의 사진이 커버)
        LocalDate today = LocalDate.now(clock);
        Plan plan = savePlan(owner, "후쿠오카", today, today.plusDays(2));
        Place first = placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("하카타 잇소", "후쿠오카")));
        Place second = placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("이치란", "후쿠오카")));
        plan.getDays().get(0).changeCompleted(true);
        plan.getDays().get(1).addNode(second, 1);
        plan.getDays().get(1).addNode(first, 0);
        plan.addMember(friend, PlanRole.EDITOR);
        em.flush();
        em.clear();
        // when
        PlanListResponse response = planService.getPlans(owner.getUserId(), listRequest(null, null, null));
        // then
        assertThat(response.totalCount()).isEqualTo(1);
        PlanSummaryResponse summary = response.plans().get(0);
        assertThat(summary.completedDayCount()).isEqualTo(1);
        assertThat(summary.totalDayCount()).isEqualTo(3);
        assertThat(summary.placeCount()).isEqualTo(2);
        assertThat(summary.coverImageUrl()).isEqualTo(first.getImageUrl());
        assertThat(summary.ownerUserId()).isEqualTo(owner.getUserId());
        assertThat(summary.members()).extracting(PlanSummaryResponse.Member::userId, PlanSummaryResponse.Member::role)
                .containsExactly(tuple(owner.getUserId(), PlanRole.OWNER), tuple(friend.getUserId(), PlanRole.EDITOR));
    }

    @Test
    @DisplayName("다가오는 여행은 종료일이 오늘이거나 그 뒤인 계획, 지난 여행은 종료일이 오늘 전인 계획이다.")
    void getPlans_period() {
        // given: 어제 끝난 계획, 오늘 끝나는 계획, 다음 주 계획
        LocalDate today = LocalDate.now(clock);
        Plan past = savePlan(owner, "과거", today.minusDays(3), today.minusDays(1));
        Plan endsToday = savePlan(owner, "오늘", today.minusDays(1), today);
        Plan future = savePlan(owner, "미래", today.plusDays(7), today.plusDays(9));
        em.flush();
        // when
        PlanListResponse upcoming = planService.getPlans(owner.getUserId(), listRequest(null, null, "UPCOMING"));
        PlanListResponse pastPlans = planService.getPlans(owner.getUserId(), listRequest(null, null, "PAST"));
        // then
        assertThat(upcoming.plans()).extracting(PlanSummaryResponse::planId)
                .containsExactlyInAnyOrder(endsToday.getPlanId(), future.getPlanId());
        assertThat(upcoming.totalCount()).isEqualTo(2);
        assertThat(pastPlans.plans()).extracting(PlanSummaryResponse::planId).containsExactly(past.getPlanId());
    }

    @Test
    @DisplayName("owner=me는 내가 만든 계획, owner=shared는 공유받은 계획만, 생략하면 둘 다 보여 주고 남의 계획은 안 보인다.")
    void getPlans_ownerFilter() {
        // given: 내 계획, 친구 계획에 내가 VIEWER, 친구만의 계획
        LocalDate today = LocalDate.now(clock);
        Plan mine = savePlan(owner, "내 계획", today, today);
        Plan shared = savePlan(friend, "공유받은 계획", today, today);
        shared.addMember(owner, PlanRole.VIEWER);
        savePlan(friend, "친구만의 계획", today, today);
        em.flush();
        // when
        PlanListResponse me = planService.getPlans(owner.getUserId(), listRequest("me", null, null));
        PlanListResponse sharedPlans = planService.getPlans(owner.getUserId(), listRequest("shared", null, null));
        PlanListResponse all = planService.getPlans(owner.getUserId(), listRequest(null, null, null));
        // then
        assertThat(me.plans()).extracting(PlanSummaryResponse::planId).containsExactly(mine.getPlanId());
        assertThat(sharedPlans.plans()).extracting(PlanSummaryResponse::planId).containsExactly(shared.getPlanId());
        assertThat(sharedPlans.plans().get(0).ownerUserId()).isEqualTo(friend.getUserId());
        assertThat(all.totalCount()).isEqualTo(2);
        assertThat(planService.countPlans(owner.getUserId())).isEqualTo(2);
    }

    @Test
    @DisplayName("status=DRAFT로 거르면 작성 중인 계획만 나온다.")
    void getPlans_statusFilter() {
        // given: 작성 중 1개, 완성 1개
        LocalDate today = LocalDate.now(clock);
        Plan draft = savePlan(owner, "작성 중", today, today);
        Plan completed = savePlan(owner, "완성", today, today);
        completed.changeStatus(PlanStatus.COMPLETED);
        em.flush();
        // when
        PlanListResponse response = planService.getPlans(owner.getUserId(), listRequest(null, PlanStatus.DRAFT, null));
        // then
        assertThat(response.plans()).extracting(PlanSummaryResponse::planId).containsExactly(draft.getPlanId());
    }

    @Test
    @DisplayName("기간을 줄이면 없어지는 일자와 그 일자의 노드·플랜B·구역이 같이 지워지고, 남은 일자가 모두 완성이면 계획이 완성된다.")
    void modifyPlan_shrinkPeriod_removesDaysAndNodes() {
        // given: 3일 계획. 1·2일차는 완성, 3일차에 노드(플랜B 포함)와 구역
        Plan plan = savePlan(owner, "후쿠오카", TestFixtures.date(11, 14), TestFixtures.date(11, 16));
        Place ramen = placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("하카타 잇소", "후쿠오카")));
        Place other = placeRepository.save(Place.create(owner, null, TestFixtures.foodContent("이치란", "후쿠오카")));
        plan.getDays().get(0).changeCompleted(true);
        plan.getDays().get(1).changeCompleted(true);
        PlanDay lastDay = plan.getDays().get(2);
        lastDay.addArea("텐진", null, null);
        lastDay.addNode(ramen, 0).addAlternative(other);
        em.flush();
        em.clear();
        // when: 종료일을 11/15로 당기면
        PlanDetailResponse response = planService.modifyPlan(plan.getPlanId(), owner.getUserId(),
                new PlanUpdateRequest(null, null, TestFixtures.date(11, 15), null, null, null, null));
        em.flush();
        em.clear();
        // then: 3일차·노드·플랜B·구역이 지워지고 상태는 COMPLETED
        assertThat(response.days()).extracting(PlanDetailResponse.Day::dayNumber).containsExactly(1, 2);
        assertThat(response.status()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(count("PlanNode") + count("PlanAlternative") + count("PlanDayArea")).isZero();
        assertThat(count("PlanDay")).isEqualTo(2);
        assertThat(placeRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("시작일을 옮기고 기간을 늘리면 남은 일자의 날짜가 다시 매겨지고 새 일자가 붙어 계획이 작성 중으로 돌아간다.")
    void modifyPlan_moveAndExtend() {
        // given: 완성된 2일 계획(11/14~11/15)
        Plan plan = savePlan(owner, "후쿠오카", TestFixtures.date(11, 14), TestFixtures.date(11, 15));
        plan.getDays().forEach(day -> day.changeCompleted(true));
        plan.changeStatus(PlanStatus.COMPLETED);
        em.flush();
        em.clear();
        // when: 11/20~11/22로 바꾸고 이름·도시도 바꾸면
        PlanDetailResponse response = planService.modifyPlan(plan.getPlanId(), owner.getUserId(),
                new PlanUpdateRequest("오사카 2박 3일", TestFixtures.date(11, 20), TestFixtures.date(11, 22),
                        "오사카", "ChIJ-osaka", 34.6937, 135.5023));
        // then
        assertThat(response.days()).extracting(PlanDetailResponse.Day::dayNumber, PlanDetailResponse.Day::date,
                        PlanDetailResponse.Day::isCompleted)
                .containsExactly(tuple(1, TestFixtures.date(11, 20), true), tuple(2, TestFixtures.date(11, 21), true),
                        tuple(3, TestFixtures.date(11, 22), false));
        assertThat(response.days()).allSatisfy(day -> assertThat(day.planDayId()).isNotNull());
        assertThat(response.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(response.name()).isEqualTo("오사카 2박 3일");
        assertThat(response.city()).isEqualTo("오사카");
    }

    @Test
    @DisplayName("기간을 바꿨을 때 시작일이 종료일보다 늦어지면 여행 기간 오류로 실패한다.")
    void modifyPlan_invalidPeriod() {
        // given: 11/14~11/16 계획
        Plan plan = savePlan(owner, "후쿠오카", TestFixtures.date(11, 14), TestFixtures.date(11, 16));
        // when / then: 시작일만 11/20으로 바꾸면
        assertThatThrownBy(() -> planService.modifyPlan(plan.getPlanId(), owner.getUserId(),
                new PlanUpdateRequest(null, TestFixtures.date(11, 20), null, null, null, null, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PLAN_PERIOD);
    }

    @Test
    @DisplayName("남의 비공유 계획은 수정·삭제하려 해도 있다는 것도 알리지 않고 404로 실패한다.")
    void modifyAndRemove_stranger_notFound() {
        // given: 친구의 비공유 계획
        Plan plan = savePlan(friend, "친구 계획", TestFixtures.date(11, 14), TestFixtures.date(11, 15));
        PlanUpdateRequest request = new PlanUpdateRequest("바꿈", null, null, null, null, null, null);
        // when / then
        assertThatThrownBy(() -> planService.modifyPlan(plan.getPlanId(), owner.getUserId(), request))
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_NOT_FOUND);
        assertThatThrownBy(() -> planService.removePlan(plan.getPlanId(), owner.getUserId()))
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("계획을 복제하면 부른 사람이 OWNER인 작성 중 계획이 되고 일자·구역·노드·플랜B·숙소는 같은 카드로, 완성 여부와 공유는 꺼진 채 복사된다.")
    void copyPlan_copiesStructure() {
        // given: 친구가 읽기 전용으로 공유한 완성 계획
        Plan source = savePlan(friend, "후쿠오카", TestFixtures.date(11, 14), TestFixtures.date(11, 15));
        Place ramen = placeRepository.save(Place.create(friend, null, TestFixtures.foodContent("하카타 잇소", "후쿠오카")));
        Place other = placeRepository.save(Place.create(friend, null, TestFixtures.foodContent("이치란", "후쿠오카")));
        Place hotel = placeRepository.save(Place.create(friend, null, TestFixtures.foodContent("호텔", "후쿠오카")));
        PlanDay day1 = source.getDays().get(0);
        day1.addArea("하카타", null, null);
        day1.addNode(other, 1);
        day1.addNode(ramen, 0).addAlternative(hotel);
        source.addStay(hotel);
        source.getDays().forEach(day -> day.changeCompleted(true));
        source.changeStatus(PlanStatus.COMPLETED);
        source.changeShareType(ShareType.READ_ONLY);
        em.flush();
        em.clear();
        // when: 내가 복제하면
        Long copyId = planService.copyPlan(source.getPlanId(), owner.getUserId()).planId();
        em.flush();
        em.clear();
        // then
        Plan copy = planRepository.findById(copyId).orElseThrow();
        assertThat(copyId).isNotEqualTo(source.getPlanId());
        assertThat(copy.getUser().getUserId()).isEqualTo(owner.getUserId());
        assertThat(copy.getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(copy.getShareType()).isNull();
        assertThat(copy.getMembers()).extracting(m -> m.getUser().getUserId(), m -> m.getRole())
                .containsExactly(tuple(owner.getUserId(), PlanRole.OWNER));
        assertThat(copy.getDays()).extracting(PlanDay::getDayNumber, PlanDay::isCompleted)
                .containsExactly(tuple(1, false), tuple(2, false));
        PlanDay copiedDay = copy.getDays().get(0);
        assertThat(copiedDay.getAreas()).extracting("name").containsExactly("하카타");
        assertThat(copiedDay.getNodes()).extracting(n -> n.getPlace().getPlaceId(), PlanNode::getOrderIndex)
                .containsExactly(tuple(ramen.getPlaceId(), 0), tuple(other.getPlaceId(), 1));
        assertThat(copiedDay.getNodes().get(0).getAlternatives()).extracting(a -> a.getPlace().getPlaceId())
                .containsExactly(hotel.getPlaceId());
        assertThat(copy.getStays()).extracting(s -> s.getPlace().getPlaceId()).containsExactly(hotel.getPlaceId());
    }

    @Test
    @DisplayName("남의 비공유 계획은 복제할 수 없고 404로 실패한다.")
    void copyPlan_notShared_notFound() {
        // given: 친구의 비공유 계획
        Plan plan = savePlan(friend, "친구 계획", TestFixtures.date(11, 14), TestFixtures.date(11, 15));
        // when / then
        assertThatThrownBy(() -> planService.copyPlan(plan.getPlanId(), owner.getUserId()))
                .extracting("errorCode").isEqualTo(ErrorCode.PLAN_NOT_FOUND);
    }

    private Plan savePlan(User user, String name, LocalDate startDate, LocalDate endDate) {
        Plan plan = Plan.create(user, name, startDate, endDate, "후쿠오카", null, null, null);
        plan.addMember(user, PlanRole.OWNER);
        int dayCount = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        for (int dayNumber = 1; dayNumber <= dayCount; dayNumber++) {
            plan.addDay(dayNumber, startDate.plusDays(dayNumber - 1));
        }
        return planRepository.save(plan);
    }

    private PlanCreateRequest createRequest(String city, LocalDate startDate, LocalDate endDate) {
        return new PlanCreateRequest(city + " 여행", startDate, endDate, city, null, null, null);
    }

    private PlanListRequest listRequest(String owner, PlanStatus status, String period) {
        return new PlanListRequest(owner, status, period, null, null, null);
    }

    private long count(String entityName) {
        return em.createQuery("select count(e) from " + entityName + " e", Long.class).getSingleResult();
    }

    private PlaceContent tipContent(String name, String city) {
        return new PlaceContent(Category.TIP, name, null, null, null, null, city, null, null, null, List.of(),
                null, null, null, null, null, "교통패스", city, "640엔", null, null, null, null);
    }
}
