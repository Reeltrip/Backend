package com.reeltrip.infra.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

/** S4-7a 구간 막대: Google의 길 안내 단계를 화면에 보일 구간으로 묶어요. */
class GoogleRouteSegmentsTest {

    private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");

    @Test
    @DisplayName("택시 경로는 길 안내 단계가 여러 개여도 택시 구간 하나로, 총 소요시간과 같은 분으로 내려준다.")
    void driveStepsBecomeOneTaxiSegment() throws Exception {
        // given: 교통 반영 총 16분, 단계 10개(정적 시간 합 13분)
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            steps.add(step("DRIVE", 78));
        }
        // when
        var result = onlyMode("DRIVE", route(960, steps));
        // then
        assertThat(result.segments()).extracting(RouteEstimate.Segment::mode).containsExactly("TAXI");
        assertThat(result.segments().getFirst().minutes()).isEqualTo(16);
    }

    @Test
    @DisplayName("도보만 있는 경로는 짧은 단계를 하나로 합쳐 구간 분의 합이 총 소요시간을 넘지 않는다.")
    void walkStepsAreMergedWithoutRoundingUpEachStep() throws Exception {
        // given: 61초 단계 10개 = 610초(11분). 단계마다 올림하면 20분이 됐어요.
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            steps.add(step("WALK", 61));
        }
        // when
        var result = onlyMode("WALK", route(610, steps));
        // then
        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().getFirst().mode()).isEqualTo("WALK");
        assertThat(result.segments().getFirst().minutes()).isEqualTo(11);
    }

    @Test
    @DisplayName("대중교통 경로는 이어진 도보를 합치고 노선마다 구간을 따로 두며, 구간 분의 합은 총 소요시간과 같다.")
    void transitKeepsEachRideAndMergesWalks() throws Exception {
        // given: 도보 2단계 → 지하철 A → 도보 2단계 → 지하철 B → 도보 (총 1290초 = 22분)
        var steps = List.of(step("WALK", 60), step("WALK", 150), ride("SUBWAY", "미도스지선", "난바역", 600),
                step("WALK", 30), step("WALK", 30), ride("SUBWAY", "사카이스지선", "닛폰바시역", 300), step("WALK", 120));
        // when
        var result = onlyMode("TRANSIT", route(1290, steps));
        // then
        assertThat(result.segments()).extracting(RouteEstimate.Segment::mode)
                .containsExactly("WALK", "SUBWAY", "WALK", "SUBWAY", "WALK");
        assertThat(result.segments()).extracting(RouteEstimate.Segment::lineName)
                .containsExactly(null, "미도스지선", null, "사카이스지선", null);
        assertThat(result.segments().stream().mapToInt(RouteEstimate.Segment::minutes).sum()).isEqualTo(22);
    }

    @Test
    @DisplayName("아주 짧은 도보도 1분 이상으로 보이고, 그래도 구간 분의 합은 총 소요시간과 같다.")
    void shortWalkStillVisibleAndTotalsMatch() throws Exception {
        // given: 도보 20초 → 지하철 1200초 → 도보 20초 (총 1240초 = 21분)
        var steps = List.of(step("WALK", 20), ride("SUBWAY", "미도스지선", "우메다역", 1200), step("WALK", 20));
        // when
        var result = onlyMode("TRANSIT", route(1240, steps));
        // then
        assertThat(result.segments()).extracting(RouteEstimate.Segment::minutes).allMatch(minutes -> minutes >= 1);
        assertThat(result.segments().stream().mapToInt(RouteEstimate.Segment::minutes).sum()).isEqualTo(21);
        assertThat(result.segments().get(1).minutes()).isEqualTo(19);
    }

    /** 고른 교통수단만 200으로 응답하고 나머지는 실패시켜요. 요청 순서는 TRANSIT, WALK, DRIVE예요. */
    @SuppressWarnings("unchecked")
    private RouteEstimate onlyMode(String mode, String body) throws Exception {
        var http = mock(BoundedHttp.class);
        var ok = mock(HttpResponse.class);
        var fail = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn(body.getBytes());
        when(fail.statusCode()).thenReturn(404);
        when(http.send(any(), anyInt(), any())).thenReturn(mode.equals("TRANSIT") ? ok : fail,
                mode.equals("WALK") ? ok : fail, mode.equals("DRIVE") ? ok : fail);
        var client = new GoogleRoutesClient(http, new ObjectMapper(), "test-key", Clock.fixed(NOW, ZoneOffset.UTC));
        return client.route(new RouteEstimate.Point(34.66, 135.50), new RouteEstimate.Point(34.69, 135.52),
                NOW.plusSeconds(3600));
    }

    private static String route(int durationSeconds, List<String> steps) {
        return "{\"routes\":[{\"duration\":\"" + durationSeconds + "s\",\"legs\":[{\"steps\":[" + String.join(",", steps)
                + "]}]}]}";
    }

    private static String step(String travelMode, int seconds) {
        return "{\"staticDuration\":\"" + seconds + "s\",\"travelMode\":\"" + travelMode + "\"}";
    }

    private static String ride(String vehicle, String line, String stop, int seconds) {
        return "{\"staticDuration\":\"" + seconds + "s\",\"travelMode\":\"TRANSIT\",\"transitDetails\":{\"transitLine\":"
                + "{\"nameShort\":\"" + line + "\",\"vehicle\":{\"type\":\"" + vehicle + "\"}},"
                + "\"stopDetails\":{\"arrivalStop\":{\"name\":\"" + stop + "\"}}}}";
    }
}
