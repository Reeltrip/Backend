package com.reeltrip.infra.fetcher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinnedHttpTransportTest {

    @Test
    @DisplayName("고정 길이·청크·연결 종료·gzip·중간 응답을 제한 안에서 읽는다.")
    void readsSupportedFraming() throws Exception {
        // given / when / then
        assertThat(read("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello").body()).isEqualTo(bytes("hello"));
        assertThat(read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2;ext=value\r\nhe\r\n3\r\nllo\r\n0\r\nX-End: yes\r\n\r\n")
                .body()).isEqualTo(bytes("hello"));
        assertThat(read("HTTP/1.0 200 OK\r\n\r\nhello").body()).isEqualTo(bytes("hello"));
        assertThat(read("HTTP/1.1 103 Early Hints\r\nLink: ignored\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello")
                .body()).isEqualTo(bytes("hello"));
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(bytes("hello"));
        }
        var wire = new ByteArrayOutputStream();
        wire.writeBytes(bytes("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: " + compressed.size() + "\r\n\r\n"));
        wire.writeBytes(compressed.toByteArray());
        assertThat(PinnedHttpTransport.readResponse(new ByteArrayInputStream(wire.toByteArray())).body()).isEqualTo(bytes("hello"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.1 200 OK\r\nContent-Length: -1\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\na",
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\na",
            "HTTP/1.1 200 OK\r\nContent-Length: 1048577\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nshort",
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: gzip, chunked\r\n\r\n",
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nxyz\r\n",
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n100001\r\n",
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1\r\nabc\r\n",
            "HTTP/1.1 200 OK\n\n", "HTTP/2 200 OK\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Encoding: br\r\n\r\n"})
    @DisplayName("모호한 프레이밍·잘린 본문·과대 길이·잘못된 청크를 거부한다.")
    void rejectsMalformedResponses(String wire) {
        // given / when / then
        assertThatThrownBy(() -> read(wire)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("압축 해제 뒤에도 본문 크기를 검사하고 긴 헤더를 거부한다.")
    void boundsHeadersAndInflatedBody() throws Exception {
        // given
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(new byte[PinnedHttpTransport.MAX_BODY + 1]);
        }
        var wire = new ByteArrayOutputStream();
        wire.writeBytes(bytes("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: " + compressed.size() + "\r\n\r\n"));
        wire.writeBytes(compressed.toByteArray());
        // when / then
        assertThatThrownBy(() -> PinnedHttpTransport.readResponse(new ByteArrayInputStream(wire.toByteArray())))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> read("HTTP/1.1 200 OK\r\nX-Long: " + "a".repeat(8193) + "\r\n\r\n"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("실제 소켓은 DNS 재조회 없이 전달받은 IP에 연결하고 원래 Host만 보낸다.")
    void pinsAddressAndDoesNotSendCredentials() throws Exception {
        // given: 전송 계층만 로컬 고정 서버에 연결해요. 공개 주소 정책은 별도로 검증해요.
        var transport = new PinnedHttpTransport();
        try (var server = new ServerSocket(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            var request = new CompletableFuture<String>();
            executor.submit(() -> {
                try (var socket = server.accept()) {
                    var input = socket.getInputStream();
                    var buffer = new StringBuilder();
                    while (!buffer.toString().endsWith("\r\n\r\n")) {
                        int value = input.read();
                        if (value < 0 || buffer.length() > 4096) {
                            throw new IOException("잘못된 요청");
                        }
                        buffer.append((char) value);
                    }
                    request.complete(buffer.toString());
                    socket.getOutputStream().write(bytes("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello"));
                } catch (IOException e) {
                    request.completeExceptionally(e);
                }
            });
            URI uri = URI.create("http://does-not-resolve.invalid:" + server.getLocalPort() + "/trip?q=1#fragment");
            // when
            var response = transport.exchange(uri, InetAddress.getByName("127.0.0.1"), new FetchDeadline(Duration.ofSeconds(2)));
            // then
            assertThat(response.body()).isEqualTo(bytes("hello"));
            assertThat(request.get(2, TimeUnit.SECONDS)).contains("GET /trip?q=1 HTTP/1.1", "Host: does-not-resolve.invalid:")
                    .doesNotContain("Authorization:", "Cookie:", "fragment");
        } finally {
            transport.shutdown();
        }
    }

    @Test
    @DisplayName("실제 소켓의 본문이 끝나지 않아도 전체 기한에서 소켓을 닫는다.")
    void closesAtAbsoluteDeadline() throws Exception {
        // given
        var transport = new PinnedHttpTransport();
        var release = new CountDownLatch(1);
        try (var server = new ServerSocket(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.getOutputStream().write(bytes("HTTP/1.1 200 OK\r\nContent-Length: 999\r\n\r\na"));
                    release.await(2, TimeUnit.SECONDS);
                } catch (IOException | InterruptedException ignored) {
                }
            });
            // when / then
            long started = System.nanoTime();
            assertThatThrownBy(() -> transport.exchange(URI.create("http://example.test:" + server.getLocalPort()),
                    InetAddress.getByName("127.0.0.1"), new FetchDeadline(Duration.ofMillis(250))))
                    .isInstanceOf(IOException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
            release.countDown();
        } finally {
            release.countDown();
            transport.shutdown();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static PinnedHttpTransport.Response read(String wire) throws IOException {
        return PinnedHttpTransport.readResponse(new ByteArrayInputStream(bytes(wire)));
    }
}
