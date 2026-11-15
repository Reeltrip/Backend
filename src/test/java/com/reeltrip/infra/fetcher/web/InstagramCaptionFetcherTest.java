package com.reeltrip.infra.fetcher.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.reeltrip.global.exception.BusinessException;

class InstagramCaptionFetcherTest {
    private final SafeWebClient web = mock(SafeWebClient.class);
    private final InstagramCaptionFetcher fetcher = new InstagramCaptionFetcher(web, new HtmlContentExtractor());
    private final URI uri = URI.create("https://www.instagram.com/reel/Trip_123/");

    @Test @DisplayName("릴스 캡션만 추출하고 공유 토큰을 외부 요청에서 제외한다.")
    void captionOnlyDoesNotPretendToReadVideo() throws Exception {
        // given
        when(web.fetch(eq(uri), any())).thenReturn(page("<meta property='og:url' content='" + uri + "'>"
                + "<meta property='og:description' content='10 likes - trip: &quot;제주 해변 카페 여행&quot;'>"));
        // when
        var result = fetcher.fetch(uri + "?igsh=secret-sharing-token");
        // then
        assertThat(result.text()).isEqualTo("제주 해변 카페 여행");
        assertThat(result.url()).contains("?igsh=");
        assertThat(result.thumbnailUrl()).isNull();
        verify(web).fetch(eq(uri), any());
    }

    @ParameterizedTest @ValueSource(strings={"", "Log in to Instagram", "See Instagram photos", "10 likes - user: &quot;&quot;"})
    @DisplayName("빈 캡션과 차단 문구는 링크 실패로 처리한다.")
    void absentOrBlockedCaptionFails(String caption) throws Exception {
        // given
        when(web.fetch(any(), any())).thenReturn(page("<meta property='og:url' content='"+uri+"'>"
                + "<meta property='og:description' content='"+caption+"'>"));
        // when / then
        assertThatThrownBy(()->fetcher.fetch(uri.toString())).isInstanceOf(BusinessException.class);
    }

    @Test @DisplayName("로그인 이동과 네트워크 실패는 해당 릴스만 실패시킨다.")
    void loginRedirectAndNetworkFailureStayLinkFailures() throws Exception {
        // given
        when(web.fetch(any(), any())).thenReturn(new SafeWebClient.Page(URI.create("https://www.instagram.com/accounts/login/"),
                "text/html", new byte[0])).thenThrow(new java.io.IOException());
        // when / then
        assertThatThrownBy(()->fetcher.fetch(uri.toString())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->fetcher.fetch(uri.toString())).isInstanceOf(BusinessException.class);
    }

    private SafeWebClient.Page page(String head) {
        return new SafeWebClient.Page(uri, "text/html; charset=UTF-8", ("<html><head>"+head+"</head><body></body></html>")
                .getBytes(StandardCharsets.UTF_8));
    }
}
