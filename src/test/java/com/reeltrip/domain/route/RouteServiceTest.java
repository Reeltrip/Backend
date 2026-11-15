package com.reeltrip.domain.route;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import com.reeltrip.domain.place.service.PlaceRouteAccessService;
import com.reeltrip.global.exception.*;
import com.reeltrip.infra.map.*;

class RouteServiceTest {
    private final PlaceRouteAccessService access=mock(PlaceRouteAccessService.class);
    private final RoutesClient provider=mock(RoutesClient.class);
    private final Clock clock=mock(Clock.class);
    private final RouteService service=new RouteService(access,provider,clock);
    private final Instant now=Instant.parse("2026-11-14T01:00:00Z");
    @BeforeEach void setup() throws Exception {
        when(clock.instant()).thenReturn(now); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(access.point(1L,2L)).thenReturn(new RouteEstimate.Point(35,135));
        when(access.point(1L,3L)).thenReturn(new RouteEstimate.Point(35.1,135.1));
        when(provider.route(any(),any(),any())).thenReturn(new RouteEstimate(Duration.ofMinutes(18),List.of(new RouteEstimate.Segment("WALK",18,null,null))));
    }

    @Test @DisplayName("기본 출발 시각은 Clock을 쓰고 서울 시각과 도착 시각을 반환한다.")
    void usesClockAndSeoulTime() {
        // given / when
        var result=service.get(1L,2L,3L,null);
        // then
        assertThat(result.departAt().toString()).isEqualTo("2026-11-14T10:00+09:00");
        assertThat(result.arriveAt()).isEqualTo(result.departAt().plusMinutes(18)); assertThat(result.totalMinutes()).isEqualTo(18);
    }

    @Test @DisplayName("허용된 제공자만 30초 캐시를 쓰며 캐시 적중이어도 권한을 다시 확인한다.")
    void cacheNeverBypassesAuthorization() throws Exception {
        // given
        when(provider.cacheable()).thenReturn(true);
        // when
        service.get(1L,2L,3L,null); service.get(1L,2L,3L,null);
        // then
        verify(provider,times(1)).route(any(),any(),any()); verify(access,times(2)).point(1L,2L);
        when(access.point(1L,2L)).thenThrow(new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        assertThatThrownBy(()->service.get(1L,2L,3L,null)).isInstanceOf(BusinessException.class);
    }

    @Test @DisplayName("캐시가 만료되면 다시 조회하고 실패 결과는 캐시하지 않는다.")
    void expiresAndDoesNotCacheFailure() throws Exception {
        // given
        when(provider.cacheable()).thenReturn(true);
        service.get(1L,2L,3L,null); when(clock.instant()).thenReturn(now.plusSeconds(31));
        // when / then
        service.get(1L,2L,3L,null); verify(provider,times(2)).route(any(),any(),any());
        when(clock.instant()).thenReturn(now.plusSeconds(70));
        when(provider.route(any(),any(),any())).thenThrow(new java.io.IOException());
        assertThatThrownBy(()->service.get(1L,2L,3L,null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.get(1L,2L,3L,null)).isInstanceOf(BusinessException.class);
        verify(provider,times(4)).route(any(),any(),any());
    }
}
