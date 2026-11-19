package com.reeltrip.infra.fetcher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RemoteImageClientTest {
    private final PinnedHttpTransport transport = mock(PinnedHttpTransport.class);
    private final PublicNetworkPolicy policy = new PublicNetworkPolicy(host ->
            new InetAddress[]{InetAddress.getByName(host.equals("169.254.169.254") ? host : "8.8.8.8")});
    private final RemoteImageClient client = new RemoteImageClient(policy, transport);

    @AfterEach
    void close() {
        policy.shutdown();
    }

    @Test
    @DisplayName("공개 https 사진을 내려받아 바이트와 형식을 돌려준다.")
    void downloadsPublicImage() throws Exception {
        // given
        when(transport.exchange(eq(URI.create("https://cdn.example/a.jpg?oe=1")), any(), any()))
                .thenReturn(new PinnedHttpTransport.Response(200, Map.of("content-type", "image/jpeg"), new byte[]{1, 2, 3}));
        // when
        var image = client.download("https://cdn.example/a.jpg?oe=1", Duration.ofSeconds(2));
        // then
        assertThat(image.bytes()).containsExactly(1, 2, 3);
        assertThat(image.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("리디렉션을 따라가되 내부 주소로 가면 더 접속하지 않는다.")
    void followsPublicRedirectButNotPrivate() throws Exception {
        // given: 공개 주소로 한 번 이동
        when(transport.exchange(eq(URI.create("https://cdn.example/a")), any(), any()))
                .thenReturn(new PinnedHttpTransport.Response(302, Map.of("location", "/b.png"), new byte[0]));
        when(transport.exchange(eq(URI.create("https://cdn.example/b.png")), any(), any()))
                .thenReturn(new PinnedHttpTransport.Response(200, Map.of("content-type", "image/png; x=1"), new byte[]{9}));
        // when / then
        assertThat(client.download("https://cdn.example/a", Duration.ofSeconds(2)).contentType()).isEqualTo("image/png");

        // given: 내부 주소로 이동
        when(transport.exchange(eq(URI.create("https://cdn.example/c")), any(), any()))
                .thenReturn(new PinnedHttpTransport.Response(302, Map.of("location", "https://169.254.169.254/x"), new byte[0]));
        // when / then
        assertThatThrownBy(() -> client.download("https://cdn.example/c", Duration.ofSeconds(2))).isInstanceOf(IOException.class);
        verify(transport, never()).exchange(eq(URI.create("https://169.254.169.254/x")), any(), any());
    }

    @Test
    @DisplayName("https가 아니거나 내부 주소면 접속하기 전에 거절한다.")
    void rejectsBeforeConnecting() {
        // when / then
        assertThatThrownBy(() -> client.download("http://cdn.example/a.jpg", Duration.ofSeconds(2))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> client.download("https://localhost/a.jpg", Duration.ofSeconds(2))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> client.download("https://169.254.169.254/a.jpg", Duration.ofSeconds(2))).isInstanceOf(IOException.class);
        verifyNoInteractions(transport);
    }

    @Test
    @DisplayName("사진이 아닌 응답과 실패 응답은 오류로 알린다.")
    void rejectsNonImage() throws Exception {
        // given
        when(transport.exchange(eq(URI.create("https://cdn.example/page")), any(), any()))
                .thenReturn(new PinnedHttpTransport.Response(200, Map.of("content-type", "text/html"), new byte[]{1}));
        when(transport.exchange(eq(URI.create("https://cdn.example/gone.jpg")), any(), any()))
                .thenReturn(new PinnedHttpTransport.Response(403, Map.of("content-type", "image/jpeg"), new byte[]{1}));
        // when / then
        assertThatThrownBy(() -> client.download("https://cdn.example/page", Duration.ofSeconds(2))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> client.download("https://cdn.example/gone.jpg", Duration.ofSeconds(2))).isInstanceOf(IOException.class);
    }
}
