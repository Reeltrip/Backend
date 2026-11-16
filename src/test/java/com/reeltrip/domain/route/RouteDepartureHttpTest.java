package com.reeltrip.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.map.*;
import com.reeltrip.support.TestFixtures;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.address=127.0.0.1")
class RouteDepartureHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired PlaceRepository places;
    @Autowired JwtProvider jwt;
    @MockitoBean RoutesClient provider;

    @Test @DisplayName("실제 HTTP에서 현재 경로와 정밀한 지정 시각을 구분하고 잘못된 시각·미인증을 거부한다.")
    void realHttpDepartureAndFailures() throws Exception {
        // given
        var owner=users.save(TestFixtures.user(UUID.randomUUID()+"@example.test"));
        var from=places.save(Place.create(owner,null,TestFixtures.foodContent("출발","후쿠오카")));
        var to=places.save(Place.create(owner,null,TestFixtures.foodContent("도착","후쿠오카")));
        when(provider.route(any(),any(),any())).thenReturn(new RouteEstimate(Duration.ofMinutes(4),List.of(new RouteEstimate.Segment("WALK",4,null,null))));
        var headers=new HttpHeaders();headers.setBearerAuth(jwt.createAccessToken(owner.getUserId()));
        var request=new HttpEntity<>(headers);String path="/routes?from="+from.getPlaceId()+"&to="+to.getPlaceId();
        // when / then
        var immediate=http.exchange(path,HttpMethod.GET,request,JsonNode.class);
        assertThat(immediate.getStatusCode().value()).isEqualTo(200);
        assertThat(immediate.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(provider).route(any(),any(),isNull());
        String future="2026-11-20T01:00:00.123456789Z";
        var explicit=http.exchange(path+"&departAt="+future,HttpMethod.GET,request,JsonNode.class);
        assertThat(explicit.getStatusCode().value()).isEqualTo(200);
        assertThat(OffsetDateTime.parse(explicit.getBody().path("departAt").asText()).toInstant()).isEqualTo(Instant.parse(future));
        verify(provider).route(any(),any(),eq(Instant.parse(future)));
        assertThat(http.exchange(path+"&departAt=bad",HttpMethod.GET,request,JsonNode.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity(path,String.class).getStatusCode().value()).isEqualTo(401);
        verify(provider,times(2)).route(any(),any(),any());
    }
}
