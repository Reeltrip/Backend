package com.reeltrip.infra.fetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.global.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** 운영 요청 주소는 확인하고, 테스트 전송 계층에서만 로컬 고정 응답 서버로 바꿔요. */
class YoutubeOembedHttpTest {

    private HttpServer server;
    private HttpClient transport;
    private YoutubeOembedClient client;
    private ExecutorService executor;
    private final AtomicInteger privateHits = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/private", exchange -> {
            privateHits.incrementAndGet();
            exchange.close();
        });
        server.start();
        transport = YoutubeOembedClient.newClient();
        var http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest original = invocation.getArgument(0);
            assertThat(original.uri().getHost()).isEqualTo("www.youtube.com");
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            var local = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/oembed"))
                    .timeout(original.timeout().orElseThrow()).GET().build();
            return transport.sendAsync(local, handler);
        });
        client = new YoutubeOembedClient(new ObjectMapper(), http, Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
        transport.shutdownNow();
    }

    @Test
    @DisplayName("실제 JDK HTTP 전송에서도 청크 메타데이터를 읽고 과대 본문을 중단한다.")
    void readsAndLimitsActualChunkedBody() {
        // given
        server.createContext("/oembed", exchange -> reply(exchange, 200,
                "{\"type\":\"video\",\"title\":\"여행 영상\"}"));
        // when / then
        assertThat(client.fetch("abcDEF12345").orElseThrow().title()).isEqualTo("여행 영상");
        // given
        server.removeContext("/oembed");
        server.createContext("/oembed", exchange -> reply(exchange, 200, "x".repeat(70_000)));
        // when / then
        assertThat(client.fetch("abcDEF12345")).isEmpty();
    }

    @Test
    @DisplayName("실제 전송의 접근 불가 응답도 링크 실패가 되고 내부 주소 리디렉션은 따라가지 않는다.")
    void rejectsUnavailableAndDoesNotFollowRedirect() {
        // given
        server.createContext("/oembed", exchange -> reply(exchange, 404, "private"));
        // when / then
        assertThatThrownBy(() -> client.fetch("abcDEF12345")).isInstanceOf(BusinessException.class);
        // given
        server.removeContext("/oembed");
        server.createContext("/oembed", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/private");
            reply(exchange, 302, "");
        });
        // when / then
        assertThat(client.fetch("abcDEF12345")).isEmpty();
        assertThat(privateHits.get()).isZero();
    }

    @Test
    @DisplayName("헤더 이후 본문이 끝나지 않는 실제 HTTP 응답도 전체 시간 제한에서 취소된다.")
    void boundsSlowResponseBody() throws Exception {
        // given
        var bodyStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.createContext("/oembed", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                bodyStarted.countDown();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // when / then
        try {
            assertThat(client.fetch("abcDEF12345")).isEmpty();
            assertThat(bodyStarted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, 0);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
