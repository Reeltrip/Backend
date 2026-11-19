package com.reeltrip.infra.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

/** S4-7 계획 보기 지도: 동선을 실제 길을 따라 그릴 수 있게 길 모양을 같이 받아요 (#93) */
class GoogleRoutePolylineTest {
    private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");
    /** Google 문서의 예시 값이에요. */
    private static final String POLYLINE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Test
    @DisplayName("가장 빠른 경로의 길 모양을 같이 돌려주고, Google에 길 모양 항목을 요청한다.")
    @SuppressWarnings("unchecked")
    void returnsPolylineOfFastestRoute() throws Exception {
        // given: 대중교통 없음, 도보 20분(다른 길 모양), 택시 8분
        var http = mock(BoundedHttp.class);
        var none = response(404, "{}");
        var walk = response(200, route(1200, "WALK", "walkLine??"));
        var drive = response(200, route(480, "DRIVE", POLYLINE));
        when(http.send(any(), anyInt(), any())).thenReturn(none, walk, drive);
        // when
        var result = client(http).route(new RouteEstimate.Point(34.66, 135.50), new RouteEstimate.Point(34.69, 135.52),
                NOW.plusSeconds(3600));
        // then
        assertThat(result.segments().getFirst().mode()).isEqualTo("TAXI");
        assertThat(result.encodedPolyline()).isEqualTo(POLYLINE);
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, atLeastOnce()).send(request.capture(), anyInt(), any());
        assertThat(request.getValue().headers().firstValue("X-Goog-FieldMask").orElseThrow())
                .contains("routes.polyline.encodedPolyline");
    }

    @Test
    @DisplayName("길 모양이 없거나 형식이 이상하면 비워 두고 소요시간과 구간은 그대로 돌려준다.")
    @SuppressWarnings("unchecked")
    void missingOrBrokenPolylineIsNull() throws Exception {
        // given: 길 모양 없음
        var http = mock(BoundedHttp.class);
        var none = response(404, "{}");
        var noLine = response(200, "{\"routes\":[{\"duration\":\"600s\",\"legs\":[{\"steps\":[{\"staticDuration\":\"600s\",\"travelMode\":\"WALK\"}]}]}]}");
        when(http.send(any(), anyInt(), any())).thenReturn(none, noLine, none);
        // when
        var result = client(http).route(new RouteEstimate.Point(34.66, 135.50), new RouteEstimate.Point(34.69, 135.52), null);
        // then
        assertThat(result.encodedPolyline()).isNull();
        assertThat(result.segments().getFirst().minutes()).isEqualTo(10);

        // given: 길 모양에 쓸 수 없는 글자(공백·한글)가 섞임
        var http2 = mock(BoundedHttp.class);
        var broken = response(200, route(600, "WALK", "ab c한글"));
        when(http2.send(any(), anyInt(), any())).thenReturn(none, broken, none);
        // when / then
        assertThat(client(http2).route(new RouteEstimate.Point(34.66, 135.50), new RouteEstimate.Point(34.69, 135.52), null)
                .encodedPolyline()).isNull();
    }

    private static GoogleRoutesClient client(BoundedHttp http) {
        return new GoogleRoutesClient(http, new ObjectMapper(), "test-key", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String route(int seconds, String mode, String polyline) throws Exception {
        return "{\"routes\":[{\"duration\":\"" + seconds + "s\",\"polyline\":{\"encodedPolyline\":"
                + new ObjectMapper().writeValueAsString(polyline) + "},\"legs\":[{\"steps\":[{\"staticDuration\":\"" + seconds
                + "s\",\"travelMode\":\"" + mode + "\"}]}]}]}";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, String body) {
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
