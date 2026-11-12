package com.reeltrip.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.PlaceTestFixtures;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** 카드 삭제 때 계획 쪽 정리(decisions #9), GET /users/me 카드 수 */
@SpringBootTest
@Transactional
class PlaceServiceTest {

    @Autowired PlaceService placeService;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlanRepository planRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;
    @Autowired ObjectMapper objectMapper;

    User me;

    @BeforeEach
    void setUp() {
        me = userRepository.save(TestFixtures.user("place-service@test.com"));
    }

    @Test
    @DisplayName("계획에 쓰인 카드를 지우면 그 카드의 노드·플랜B·숙소가 같이 지워지고 다른 노드는 남는다.")
    void removePlace_usedInPlan_removesNodeAlternativeAndStay() {
        // given: 라멘 노드(플랜B 카페), 타워 노드(플랜B 라멘), 숙소 호텔
        Place ramen = save(PlaceTestFixtures.placeAt(Category.FOOD, "라멘", 33.59, 130.42));
        Place cafe = save(PlaceTestFixtures.placeAt(Category.CAFE, "카페", 33.59, 130.42));
        Place tower = save(PlaceTestFixtures.placeAt(Category.SIGHTSEEING, "타워", 33.59, 130.35));
        Place hotel = save(PlaceTestFixtures.placeAt(Category.STAY, "호텔", 33.59, 130.42));
        Plan plan = Plan.create(me, "후쿠오카", TestFixtures.date(11, 14), TestFixtures.date(11, 15),
                "후쿠오카", null, null, null);
        plan.addMember(me, PlanRole.OWNER);
        PlanDay day = plan.addDay(1, TestFixtures.date(11, 14));
        day.addNode(ramen, 0).addAlternative(cafe);
        day.addNode(tower, 1).addAlternative(ramen);
        plan.addStay(hotel);
        planRepository.save(plan);
        em.flush();
        em.clear();

        // when: 라멘과 호텔 카드를 지우면
        placeService.removePlace(me.getUserId(), ramen.getPlaceId());
        placeService.removePlace(me.getUserId(), hotel.getPlaceId());
        em.flush();
        em.clear();

        // then: 라멘 노드와 그 플랜B, 라멘이 들어간 플랜B, 호텔 숙소가 지워지고 타워 노드만 남음
        assertThat(placeRepository.findById(ramen.getPlaceId())).isEmpty();
        assertThat(placeRepository.findById(hotel.getPlaceId())).isEmpty();
        Plan reloaded = planRepository.findById(plan.getPlanId()).orElseThrow();
        assertThat(reloaded.getStays()).isEmpty();
        assertThat(reloaded.getDays().get(0).getNodes())
                .extracting(node -> node.getPlace().getPlaceId())
                .containsExactly(tower.getPlaceId());
        PlanNode towerNode = reloaded.getDays().get(0).getNodes().get(0);
        assertThat(towerNode.getAlternatives()).isEmpty();
        assertThat(em.createQuery("select count(a) from PlanAlternative a", Long.class).getSingleResult()).isZero();
        assertThat(placeRepository.findById(cafe.getPlaceId())).isPresent();
    }

    @Test
    @DisplayName("계획에 쓰인 카드는 카테고리를 바꿀 수 없다.")
    void modifyPlace_usedInPlan_categoryChange_invalid() throws Exception {
        // given: 숙소로 고른 STAY 카드와 계획에 안 쓰인 맛집 카드
        Place hotel = save(PlaceTestFixtures.placeAt(Category.STAY, "호텔", 33.59, 130.42));
        Place unused = save(PlaceTestFixtures.placeAt(Category.FOOD, "라멘", 33.59, 130.42));
        Plan plan = Plan.create(me, "후쿠오카", TestFixtures.date(11, 14), TestFixtures.date(11, 15),
                "후쿠오카", null, null, null);
        plan.addMember(me, PlanRole.OWNER);
        plan.addStay(hotel);
        planRepository.save(plan);
        em.flush();

        // when / then: 쓰인 카드의 카테고리를 바꾸면 실패
        assertThatThrownBy(() -> placeService.modifyPlace(me.getUserId(), hotel.getPlaceId(),
                objectMapper.readTree("{ \"category\": \"FOOD\" }")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        // then: 같은 카테고리로 보내는 건 변경이 아니라 통과
        assertThat(placeService.modifyPlace(me.getUserId(), hotel.getPlaceId(),
                objectMapper.readTree("{ \"category\": \"STAY\", \"name\": \"호텔 본관\" }")).name())
                .isEqualTo("호텔 본관");
        // then: 계획에 안 쓰인 카드는 자유롭게 바꿀 수 있음
        assertThat(placeService.modifyPlace(me.getUserId(), unused.getPlaceId(),
                objectMapper.readTree("{ \"category\": \"CAFE\" }")).category())
                .isEqualTo(Category.CAFE);
    }

    @Test
    @DisplayName("보관함 카드 수는 그 회원의 카드를 교통·팁까지 모두 센다.")
    void countPlaces_includesTransportAndTip() {
        // given: 내 장소 카드 1장, 팁 1장, 남의 카드 1장
        save(PlaceTestFixtures.placeAt(Category.FOOD, "라멘", 33.59, 130.42));
        save(PlaceTestFixtures.tip("환전"));
        User other = userRepository.save(TestFixtures.user("place-service-other@test.com"));
        placeRepository.save(Place.create(other, null, PlaceTestFixtures.tip("남의 팁")));
        // when / then
        assertThat(placeService.countPlaces(me.getUserId())).isEqualTo(2);
    }

    private Place save(PlaceContent content) {
        return placeRepository.save(Place.create(me, null, content));
    }
}
