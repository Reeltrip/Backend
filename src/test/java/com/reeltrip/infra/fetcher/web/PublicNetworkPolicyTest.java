package com.reeltrip.infra.fetcher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicNetworkPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.1.2.3", "127.0.0.1", "100.64.0.1", "169.254.169.254",
            "172.16.0.1", "192.168.1.1", "192.0.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1",
            "203.0.113.1", "224.0.0.1", "240.0.0.1", "::", "::1", "fc00::1", "fe80::1", "ff02::1",
            "::ffff:127.0.0.1", "64:ff9b::7f00:1", "2002:7f00:1::", "2001:db8::1", "2001::1"})
    @DisplayName("내부·메타데이터·전환·예약 주소를 공개 인터넷 주소로 취급하지 않는다.")
    void blocksNonPublicAddresses(String literal) throws Exception {
        // given / when / then
        assertThat(PublicNetworkPolicy.isPublic(InetAddress.getByName(literal))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "2001:4860:4860::8888"})
    @DisplayName("공개 IPv4·IPv6 주소를 허용한다.")
    void acceptsPublicAddresses(String literal) throws Exception {
        // given / when / then
        assertThat(PublicNetworkPolicy.isPublic(InetAddress.getByName(literal))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "http://user:secret@example.com/", "https://example.com:8443/",
            "http://localhost/", "http://x.localhost/", "http://metadata.google.internal/", "http://router.local/",
            "https://example.com./", "https://example.com:80/", "https://%31%32%37.0.0.1/"})
    @DisplayName("비HTTP·인증정보·비표준 포트·내부 호스트는 DNS 전에 거부한다.")
    void rejectsUnsafeUrlSyntax(String url) {
        // given / when / then
        assertThatThrownBy(() -> PublicNetworkPolicy.parse(url)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("공개 주소와 내부 주소가 섞인 DNS 응답은 전체를 거부한다.")
    void rejectsMixedDnsAnswers() throws Exception {
        // given
        var policy = new PublicNetworkPolicy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("127.0.0.1")});
        try {
            // when / then
            assertThatThrownBy(() -> policy.resolve(URI.create("https://rebind.example/"),
                    new FetchDeadline(Duration.ofSeconds(1)))).isInstanceOf(IOException.class);
        } finally {
            policy.shutdown();
        }
    }
}
