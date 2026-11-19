package com.reeltrip.domain.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.map.RouteEstimate;
import com.reeltrip.infra.map.RoutesClient;
import com.reeltrip.support.TestFixtures;

/** S4-7 계획 보기 지도: GET /routes가 길 모양을 같이 내려줘요 (#93) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutePolylineApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PlaceRepository places;
    @Autowired JwtProvider jwt;
    @MockitoBean RoutesClient routes;

    @Test
    @DisplayName("구간 경로에 길 모양을 같이 내려주고, 길 모양이 없으면 null로 내려준다.")
    void returnsPolyline() throws Exception {
        // given
        var owner = users.save(TestFixtures.user("route-polyline@example.test"));
        var from = places.save(Place.create(owner, null, TestFixtures.foodContent("출발", "후쿠오카")));
        var to = places.save(Place.create(owner, null, TestFixtures.foodContent("도착", "후쿠오카")));
        var other = places.save(Place.create(owner, null, TestFixtures.foodContent("다른 곳", "후쿠오카")));
        String bearer = "Bearer " + jwt.createAccessToken(owner.getUserId());
        var segments = List.of(new RouteEstimate.Segment("WALK", 18, null, null));
        when(routes.route(any(), any(), any()))
                .thenReturn(new RouteEstimate(Duration.ofMinutes(18), segments, "_p~iF~ps|U_ulLnnqC_mqNvxq`@"))
                .thenReturn(new RouteEstimate(Duration.ofMinutes(18), segments));
        // when / then
        mvc.perform(get("/routes?from=" + from.getPlaceId() + "&to=" + to.getPlaceId()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(18))
                .andExpect(jsonPath("$.polyline").value("_p~iF~ps|U_ulLnnqC_mqNvxq`@"));
        mvc.perform(get("/routes?from=" + from.getPlaceId() + "&to=" + other.getPlaceId()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polyline").value((Object) null));
    }
}
