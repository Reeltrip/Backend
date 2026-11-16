package com.reeltrip.infra.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.*;
import com.reeltrip.infra.http.BoundedHttp;

class GoogleRouteDepartureTest {
    private static final Instant NOW=Instant.parse("2026-11-16T01:00:00.750Z");
    private final BoundedHttp http=mock(BoundedHttp.class);
    private final Clock clock=mock(Clock.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final List<JsonNode> sent=new ArrayList<>();
    private final RouteEstimate.Point from=new RouteEstimate.Point(35,135),to=new RouteEstimate.Point(35.1,135.1);

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(http.send(any(),anyInt(),any())).thenAnswer(call->{
            JsonNode body=readBody(call.getArgument(0)); sent.add(body);
            // 앞선 두 호출이 끝나기까지 10초가 걸린 상황도 재현해요.
            when(clock.instant()).thenReturn(NOW.plusSeconds(10));
            var response=mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn((body.path("travelMode").asText().equals("DRIVE")
                    ?"{\"routes\":[{\"duration\":\"300s\"}]}":"{\"routes\":[{\"duration\":\"600s\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return response;
        });
    }

    @Test @DisplayName("출발 시각 생략은 느린 앞선 조회 뒤에도 Google 기본 현재 시각으로 차량 후보를 비교한다.")
    void immediateOmitsDepartureForEveryMode() throws Exception {
        // given / when
        var result=client().route(from,to,null);
        // then
        assertThat(sent).hasSize(3).allMatch(body->!body.has("departureTime"));
        assertThat(result.segments().getFirst().mode()).isEqualTo("TAXI");
    }

    @Test @DisplayName("미래 시각은 대중교통·차량에 정밀도를 보존해 보내고 도보에는 보내지 않는다.")
    void explicitFutureIsPreserved() throws Exception {
        // given
        Instant future=NOW.plusSeconds(3600).plusNanos(123456);
        // when
        client().route(from,to,future);
        // then
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).path("departureTime").asText()).isEqualTo(future.toString());
        assertThat(sent.get(1).has("departureTime")).isFalse();
        assertThat(sent.get(2).path("departureTime").asText()).isEqualTo(future.toString());
    }

    @Test @DisplayName("앞선 호출 중 지정 시각이 지나면 차량을 미래로 바꾸지 않고 다른 후보만 반환한다.")
    void elapsedFutureDoesNotSendInvalidDrive() throws Exception {
        // given
        Instant future=NOW.plusSeconds(1);
        // when
        var result=client().route(from,to,future);
        // then
        assertThat(sent).extracting(body->body.path("travelMode").asText()).containsExactly("TRANSIT","WALK");
        assertThat(sent.getFirst().path("departureTime").asText()).isEqualTo(future.toString());
        assertThat(result.duration()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test @DisplayName("명시한 과거 출발은 대중교통에 보존하고 차량 API의 과거 시각 오류를 피한다.")
    void explicitPastSkipsDrive() throws Exception {
        // given
        Instant past=NOW.minusSeconds(60);
        // when
        client().route(from,to,past);
        // then
        assertThat(sent).hasSize(2);
        assertThat(sent.getFirst().path("departureTime").asText()).isEqualTo(past.toString());
        assertThat(sent).noneMatch(body->body.path("travelMode").asText().equals("DRIVE"));
    }

    private GoogleRoutesClient client(){return new GoogleRoutesClient(http,mapper,"test-key",clock);}
    private JsonNode readBody(HttpRequest request) throws Exception {
        var done=new CompletableFuture<byte[]>();var bytes=new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer buffer){byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
            public void onError(Throwable error){done.completeExceptionally(error);}
            public void onComplete(){done.complete(bytes.toByteArray());}
        });
        return mapper.readTree(done.get(1,TimeUnit.SECONDS));
    }
}
