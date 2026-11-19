package com.reeltrip.infra.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

/** S4-7 계획 보기: 걸어갈 만한 구간은 택시 길로 빙 돌지 않고 걷는 길로 보여줘요 (#95) */
class GoogleRouteWalkPreferenceTest {
    private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");
    private static final RouteEstimate.Point FROM = new RouteEstimate.Point(34.666, 135.501);
    private static final RouteEstimate.Point TO = new RouteEstimate.Point(34.667, 135.506);

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("걸어서 15분 이내인 구간은 택시가 더 빨라도 도보 경로를 고른다.")
    void walkWithin15Minutes_preferredOverFasterTaxi() throws Exception {
        // given: 대중교통 없음, 도보 15분, 택시 9분
        var http = mock(BoundedHttp.class);
        var responses = new HttpResponse[] { response(404, "{}"), response(200, route(900, "WALK", "walk")), response(200, route(540, "DRIVE", "taxi")) };
        when(http.send(any(), anyInt(), any())).thenReturn(responses[0], responses[1], responses[2]);
        // when
        var result = client(http).route(FROM, TO, NOW.plusSeconds(3600));
        // then: 도보 경로와 그 길 모양, Google 호출은 그대로 세 번
        assertThat(result.segments()).extracting(RouteEstimate.Segment::mode).containsExactly("WALK");
        assertThat(result.segments().getFirst().minutes()).isEqualTo(15);
        assertThat(result.encodedPolyline()).isEqualTo("walk");
        verify(http, times(3)).send(any(), anyInt(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("걸어서 15분이 넘는 구간은 지금처럼 가장 빠른 수단을 고른다.")
    void walkOver15Minutes_fastestKept() throws Exception {
        // given: 대중교통 없음, 도보 16분, 택시 9분
        var http = mock(BoundedHttp.class);
        var responses = new HttpResponse[] { response(404, "{}"), response(200, route(901, "WALK", "walk")), response(200, route(540, "DRIVE", "taxi")) };
        when(http.send(any(), anyInt(), any())).thenReturn(responses[0], responses[1], responses[2]);
        // when
        var result = client(http).route(FROM, TO, NOW.plusSeconds(3600));
        // then
        assertThat(result.segments()).extracting(RouteEstimate.Segment::mode).containsExactly("TAXI");
        assertThat(result.encodedPolyline()).isEqualTo("taxi");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("걷기 15분 이내여도 도보 경로를 받지 못했으면 가장 빠른 수단을 고른다.")
    void walkMissing_fastestKept() throws Exception {
        // given: 대중교통 없음, 도보 조회 실패, 택시 5분
        var http = mock(BoundedHttp.class);
        var responses = new HttpResponse[] { response(404, "{}"), response(500, "{}"), response(200, route(300, "DRIVE", "taxi")) };
        when(http.send(any(), anyInt(), any())).thenReturn(responses[0], responses[1], responses[2]);
        // when
        var result = client(http).route(FROM, TO, NOW.plusSeconds(3600));
        // then
        assertThat(result.segments()).extracting(RouteEstimate.Segment::mode).containsExactly("TAXI");
    }

    private static GoogleRoutesClient client(BoundedHttp http) {
        return new GoogleRoutesClient(http, new ObjectMapper(), "test-key", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String route(int seconds, String mode, String polyline) {
        return "{\"routes\":[{\"duration\":\"" + seconds + "s\",\"polyline\":{\"encodedPolyline\":\"" + polyline
                + "\"},\"legs\":[{\"steps\":[{\"staticDuration\":\"" + seconds + "s\",\"travelMode\":\"" + mode + "\"}]}]}]}";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, String body) {
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
