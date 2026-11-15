package com.reeltrip.infra.map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.HttpResponse;
import java.time.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

class GoogleRoutesTest {
    @Test @DisplayName("지원 가능한 세 교통수단 중 실제 응답 시간이 가장 짧은 경로를 고른다.")
    @SuppressWarnings("unchecked")
    void selectsFastestProviderResponse() throws Exception {
        // given
        var http=mock(BoundedHttp.class); var transit=mock(HttpResponse.class); var walk=mock(HttpResponse.class); var drive=mock(HttpResponse.class);
        for(var response:new HttpResponse[]{transit,walk,drive}) { when(response.statusCode()).thenReturn(200); }
        when(transit.body()).thenReturn("{\"routes\":[{\"duration\":\"600s\",\"legs\":[{\"steps\":[{\"staticDuration\":\"600s\",\"travelMode\":\"TRANSIT\",\"transitDetails\":{\"transitLine\":{\"nameShort\":\"지하철\",\"vehicle\":{\"type\":\"SUBWAY\"}},\"stopDetails\":{\"arrivalStop\":{\"name\":\"역\"}}}}]}]}]}".getBytes());
        when(walk.body()).thenReturn("{\"routes\":[{\"duration\":\"1800s\"}]}".getBytes());
        when(drive.body()).thenReturn("{\"routes\":[{\"duration\":\"900s\"}]}".getBytes());
        when(http.send(any(),anyInt(),any())).thenReturn(transit,walk,drive);
        var client=new GoogleRoutesClient(http,new ObjectMapper(),"test-key");
        // when
        var result=client.route(new RouteEstimate.Point(35,135),new RouteEstimate.Point(35.1,135.1),Instant.now());
        // then
        assertThat(result.duration()).isEqualTo(Duration.ofMinutes(10)); assertThat(result.segments().getFirst().mode()).isEqualTo("SUBWAY");
        assertThat(result.segments().getFirst().stopName()).isEqualTo("역"); assertThat(client.cacheable()).isFalse();
        verify(http,times(3)).send(any(),anyInt(),any());
    }
}
