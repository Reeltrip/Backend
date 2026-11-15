package com.reeltrip.domain.route;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.place.service.PlaceRouteAccessService;
import com.reeltrip.domain.plan.entity.*;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.map.*;
import com.reeltrip.support.TestFixtures;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class RouteApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PlaceRepository places;
    @Autowired PlanRepository plans;
    @Autowired PlaceRouteAccessService access;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @MockitoBean RoutesClient routes;

    @Test @DisplayName("로그인한 회원의 카드 경로를 반환하고 미인증·남의 카드는 거부한다.")
    void checksAuthorizationAndReturnsContract() throws Exception {
        // given
        var owner=users.save(TestFixtures.user("route-owner@example.test"));
        var other=users.save(TestFixtures.user("route-other@example.test"));
        var from=places.save(Place.create(owner,null,TestFixtures.foodContent("출발","후쿠오카")));
        var to=places.save(Place.create(owner,null,TestFixtures.foodContent("도착","후쿠오카")));
        when(routes.route(any(),any(),any())).thenReturn(new RouteEstimate(Duration.ofMinutes(18),List.of(new RouteEstimate.Segment("WALK",18,null,null))));
        String url="/routes?from="+from.getPlaceId()+"&to="+to.getPlaceId();
        // when / then
        mvc.perform(get(url).header("Authorization","Bearer "+jwt.createAccessToken(owner.getUserId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalMinutes").value(18)).andExpect(jsonPath("$.segments[0].mode").value("WALK"));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization","Bearer "+jwt.createAccessToken(other.getUserId()))).andExpect(status().isNotFound());
    }

    @Test @DisplayName("공유 계획의 기존 멤버는 포함된 카드 좌표를 읽지만 비멤버는 읽지 못한다.")
    void sharesOnlyReadablePlanCards() {
        // given
        var owner=users.save(TestFixtures.user("route-plan-owner@example.test"));
        var viewer=users.save(TestFixtures.user("route-plan-viewer@example.test"));
        var stranger=users.save(TestFixtures.user("route-plan-stranger@example.test"));
        var place=places.save(Place.create(owner,null,TestFixtures.foodContent("공유 카드","후쿠오카")));
        var date=LocalDate.of(2026,11,14);
        var plan=Plan.create(owner,"여행",date,date,"후쿠오카",null,null,null);
        plan.addMember(owner,PlanRole.OWNER); plan.addMember(viewer,PlanRole.VIEWER); plan.addDay(1,date);
        plans.saveAndFlush(plan);
        jdbc.update("insert into plan_node(plan_id,plan_day_id,place_id,order_index,created_at) values(?,?,?,?,?)",
                plan.getPlanId(),plan.getDays().getFirst().getPlanDayId(),place.getPlaceId(),0,LocalDateTime.now());
        // when / then
        assertThat(access.point(viewer.getUserId(),place.getPlaceId()).latitude()).isEqualTo(33.5889);
        assertThatThrownBy(()->access.point(stranger.getUserId(),place.getPlaceId())).isInstanceOf(BusinessException.class);
    }
}
