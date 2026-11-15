package com.reeltrip.infra.fetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

class YoutubeOembedClientTest {

    private static final String ID = "abcDEF12345";
    private static final String THUMBNAIL = "https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final YoutubeOembedClient client = new YoutubeOembedClient(mapper, http, Duration.ofMillis(100));
    private HttpRequest lastRequest;

    @Test
    @DisplayName("공식 고정 주소에 영상 ID만 보내 제목·썸네일을 읽고 응답·전체 시간 제한을 적용한다.")
    void readsMetadataFromFixedEndpoint() throws Exception {
        // given
        respond(200, json("여행 영상", THUMBNAIL), "application/json; charset=utf-8", "identity");
        // when
        var metadata = client.fetch(ID).orElseThrow();
        // then
        assertThat(metadata.title()).isEqualTo("여행 영상");
        assertThat(metadata.thumbnailUrl()).isEqualTo(THUMBNAIL);
        assertThat(lastRequest.uri()).isEqualTo(URI.create("https://www.youtube.com/oembed?format=json&url="
                + "https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DabcDEF12345"));
        assertThat(lastRequest.timeout()).contains(Duration.ofSeconds(4));
        assertThat(lastRequest.headers().firstValue("Accept-Encoding")).contains("identity");
    }

    @Test
    @DisplayName("운영 HTTP 클라이언트는 리디렉션·프록시를 사용하지 않고 연결을 2초로 제한한다.")
    void restrictsNetworkPolicy() {
        // given / when
        try (var configured = YoutubeOembedClient.newClient()) {
            // then
            assertThat(configured.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
            assertThat(configured.connectTimeout()).contains(Duration.ofSeconds(2));
            assertThat(configured.proxy().orElseThrow().select(URI.create("https://www.youtube.com/oembed")))
                    .containsExactly(Proxy.NO_PROXY);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 410})
    @DisplayName("oEmbed가 접근 불가 응답이면 링크 수집 실패로 구분한다.")
    void rejectsUnavailableVideo(int status) {
        // given
        respond(status, "비공개 영상", "text/plain", "identity");
        // when / then
        assertThatThrownBy(() -> client.fetch(ID)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SOURCE_FETCH_FAILED));
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308, 429, 500, 503})
    @DisplayName("리디렉션·호출 제한·서버 오류는 추가 접속 없이 썸네일 수집만 생략한다.")
    void skipsOptionalMetadataOnTemporaryFailure(int status) {
        // given: Location을 따라갈 로직 없이 고정 응답 하나만 처리해요.
        respond(status, "", "application/json", "identity");
        // when / then
        assertThat(client.fetch(ID)).isEmpty();
        verify(http, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "[]", "{}", "{\"type\":\"link\"}",
            "{\"type\":\"video\",", "{\"error\":\"private\"}"})
    @DisplayName("메타데이터 JSON이 깨지거나 예상 형식이 아니어도 영상 입력을 막지 않는다.")
    void skipsMalformedMetadata(String body) {
        // given
        respond(200, body, "application/json", "identity");
        // when / then
        assertThat(client.fetch(ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg",
            "https://i.ytimg.com.evil.test/vi/abcDEF12345/hqdefault.jpg",
            "https://127.0.0.1/vi/abcDEF12345/hqdefault.jpg",
            "https://user:secret@i.ytimg.com/vi/abcDEF12345/hqdefault.jpg",
            "https://i.ytimg.com:8443/vi/abcDEF12345/hqdefault.jpg",
            "https://i.ytimg.com/vi/otherVideo0/hqdefault.jpg",
            "https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg?next=http://127.0.0.1",
            "https://i.ytimg.com/vi/abcDEF12345/%2e%2e/secrets", "file:///etc/passwd"})
    @DisplayName("외부·내부·다른 영상의 썸네일 주소는 전달하거나 접속하지 않는다.")
    void discardsUnsafeThumbnail(String url) throws Exception {
        // given
        respond(200, json("여행 영상", url), "application/json", "identity");
        // when / then
        assertThat(client.fetch(ID).orElseThrow().thumbnailUrl()).isNull();
        verify(http, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("제목·썸네일이 빠진 정상 메타데이터는 null을 유지한다.")
    void acceptsMissingOptionalFields() {
        // given
        respond(200, "{\"type\":\"video\"}", "application/json", "identity");
        // when / then
        assertThat(client.fetch(ID)).contains(new YoutubeOembedClient.Metadata(null, null));
    }

    @Test
    @DisplayName("HTML·압축 응답과 최대 크기를 넘은 본문은 파싱하지 않고 생략한다.")
    void rejectsUnsafeBodies() throws Exception {
        // given / when / then
        respond(200, json("제목", THUMBNAIL), "text/html", "identity");
        assertThat(client.fetch(ID)).isEmpty();
        respond(200, json("제목", THUMBNAIL), "application/json", "gzip");
        assertThat(client.fetch(ID)).isEmpty();
        respond(200, "x".repeat(YoutubeOembedClient.MAX_BYTES + 1), "application/json", "identity");
        assertThat(client.fetch(ID)).isEmpty();
    }

    @Test
    @DisplayName("연결·DNS 실패는 썸네일을 생략하고 본문 완료가 늦으면 전체 요청을 취소한다.")
    void handlesIoFailureAndWholeBodyTimeout() {
        // given
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IOException("network")));
        // when / then
        assertThat(client.fetch(ID)).isEmpty();
        // given: 헤더가 왔어도 본문이 끝나지 않은 요청
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        // when / then
        assertThat(client.fetch(ID)).isEmpty();
        assertThat(pending.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("작업 중단은 요청을 취소하고 인터럽트 상태를 유지하며 후속 분석을 막는다.")
    void preservesInterruption() {
        // given
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        Thread.currentThread().interrupt();
        try {
            // when / then
            assertThatThrownBy(() -> client.fetch(ID)).isInstanceOf(BusinessException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(pending.isCancelled()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private String json(String title, String thumbnail) throws Exception {
        return mapper.writeValueAsString(Map.of("type", "video", "title", title, "thumbnail_url", thumbnail));
    }

    @SuppressWarnings("unchecked")
    private void respond(int status, String body, String type, String encoding) {
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            lastRequest = invocation.getArgument(0);
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            var headers = HttpHeaders.of(Map.of("Content-Type", List.of(type), "Content-Encoding", List.of(encoding)),
                    (key, value) -> true);
            var info = mock(HttpResponse.ResponseInfo.class);
            when(info.statusCode()).thenReturn(status);
            when(info.headers()).thenReturn(headers);
            try {
                var subscriber = handler.apply(info);
                subscriber.onSubscribe(mock(Flow.Subscription.class));
                subscriber.onNext(List.of(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8))));
                subscriber.onComplete();
                return subscriber.getBody().toCompletableFuture().thenApply(bytes -> {
                    HttpResponse<byte[]> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(status);
                    when(response.headers()).thenReturn(headers);
                    when(response.body()).thenReturn(bytes);
                    return response;
                });
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }
}
