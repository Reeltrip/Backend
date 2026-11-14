package com.reeltrip.infra.fetcher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeWebClientTest {
    private final PinnedHttpTransport transport = mock(PinnedHttpTransport.class);
    private final AtomicInteger lookups = new AtomicInteger();
    private final PublicNetworkPolicy policy = new PublicNetworkPolicy(host -> {
        lookups.incrementAndGet();
        return new InetAddress[]{InetAddress.getByName(host.equals("169.254.169.254") ? host : "8.8.8.8")};
    });
    private final SafeWebClient client = new SafeWebClient(policy, transport);

    @AfterEach
    void close() {
        policy.shutdown();
    }

    @Test
    @DisplayName("공개 리디렉션을 다시 검사하고 검사한 IP를 그대로 전송 계층에 넘긴다.")
    void validatesAndPinsEachHop() throws Exception {
        // given
        URI original = URI.create("https://travel.example/a");
        URI target = URI.create("https://travel.example/b");
        when(transport.exchange(eq(original), any(), any())).thenReturn(new PinnedHttpTransport.Response(302,
                Map.of("location", "/b"), new byte[0]));
        when(transport.exchange(eq(target), any(), any())).thenReturn(new PinnedHttpTransport.Response(200,
                Map.of("content-type", "text/html; charset=UTF-8"), new byte[]{1}));
        // when
        var page = client.fetch(original, deadline());
        // then
        assertThat(page.uri()).isEqualTo(target);
        assertThat(lookups.get()).isEqualTo(2);
        verify(transport).exchange(eq(original), eq(InetAddress.getByName("8.8.8.8")), any());
        verify(transport).exchange(eq(target), eq(InetAddress.getByName("8.8.8.8")), any());
    }

    @Test
    @DisplayName("내부 주소로 리디렉션되면 첫 공개 요청 이후 더 접속하지 않는다.")
    void rejectsPrivateRedirect() throws Exception {
        // given
        when(transport.exchange(any(), any(), any())).thenReturn(new PinnedHttpTransport.Response(302,
                Map.of("location", "http://169.254.169.254/latest/meta-data/"), new byte[0]));
        // when / then
        assertThatThrownBy(() -> client.fetch(URI.create("http://travel.example/"), deadline())).isInstanceOf(IOException.class);
        verify(transport, times(1)).exchange(any(), any(), any());
    }

    @Test
    @DisplayName("HTTPS 하향 이동과 반복·과도한 리디렉션을 거부한다.")
    void limitsRedirects() throws Exception {
        // given
        when(transport.exchange(any(), any(), any())).thenReturn(new PinnedHttpTransport.Response(302,
                Map.of("location", "http://travel.example/other"), new byte[0]));
        // when / then
        assertThatThrownBy(() -> client.fetch(URI.create("https://travel.example/"), deadline())).isInstanceOf(IOException.class);
        when(transport.exchange(any(), any(), any())).thenReturn(new PinnedHttpTransport.Response(302,
                Map.of("location", "/loop"), new byte[0]));
        assertThatThrownBy(() -> client.fetch(URI.create("https://travel.example/loop"), deadline())).isInstanceOf(IOException.class);
        var hops = new AtomicInteger();
        when(transport.exchange(any(), any(), any())).thenAnswer(call -> new PinnedHttpTransport.Response(302,
                Map.of("location", "/hop" + hops.incrementAndGet()), new byte[0]));
        assertThatThrownBy(() -> client.fetch(URI.create("https://travel.example/start"), deadline())).isInstanceOf(IOException.class);
        assertThat(hops.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("이미지 DNS가 내부 주소이거나 HTTP면 썸네일만 버리고 이미지에는 접속하지 않는다.")
    void treatsThumbnailAsOptional() {
        // given / when / then
        assertThat(client.thumbnail("http://travel.example/photo.jpg", URI.create("https://travel.example/"), deadline())).isNull();
        assertThat(client.thumbnail("https://169.254.169.254/photo.jpg", URI.create("https://travel.example/"), deadline())).isNull();
        assertThat(client.thumbnail("/photo.jpg", URI.create("https://travel.example/trip"), deadline()))
                .isEqualTo("https://travel.example/photo.jpg");
        verifyNoInteractions(transport);
    }

    private static FetchDeadline deadline() {
        return new FetchDeadline(Duration.ofSeconds(2));
    }
}
