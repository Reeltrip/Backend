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

    @Test @DisplayName("실제 인스타처럼 og:url에 계정명이 붙은 릴스도 캡션을 읽는다.")
    void reelWithAccountNameInOgUrl() throws Exception {
        // given: 인스타는 og:url을 /계정명/reel/코드/ 모양으로 내려줘요.
        when(web.fetch(eq(uri), any())).thenReturn(page("<meta property='og:url' content='https://www.instagram.com/space.tamnik/reel/Trip_123/'>"
                + "<meta property='og:description' content='10 likes - trip: &quot;오사카 맛집 족보&quot;'>"));
        // when
        var result = fetcher.fetch("https://www.instagram.com/reel/Trip_123/?psln=MWdjeXF5NmE2dzFtbg==");
        // then
        assertThat(result.text()).isEqualTo("오사카 맛집 족보");
    }

    @Test @DisplayName("실제 인스타 설명문처럼 따옴표 뒤에 마침표가 붙어도 좋아요 수를 빼고 캡션만 읽는다.")
    void realDescriptionEndsWithQuoteAndPeriod() throws Exception {
        // given: 실제 og:description은 '101K likes, ... : "캡션". ' 모양이에요.
        when(web.fetch(eq(uri), any())).thenReturn(page("<meta property='og:url' content='https://www.instagram.com/space.tamnik/reel/Trip_123/'>"
                + "<meta property='og:description' content='101K likes, 2,356 comments - space_tamnik on May 6, 2025: &quot;오사카 맛집 족보&quot;. '>"));
        // when
        var result = fetcher.fetch(uri.toString());
        // then
        assertThat(result.text()).isEqualTo("오사카 맛집 족보");
    }

    @Test @DisplayName("인스타 게시물(/p/) 주소도 공유 값을 빼고 게시물 캡션을 읽는다.")
    void postLinkReadsCaption() throws Exception {
        // given: 사진 여러 장 게시물
        URI post = URI.create("https://www.instagram.com/p/Dbz6dj_gVK_/");
        when(web.fetch(eq(post), any())).thenReturn(new SafeWebClient.Page(post, "text/html; charset=UTF-8",
                ("<html><head><meta property='og:url' content='https://www.instagram.com/su.vwv_eat/p/Dbz6dj_gVK_/'>"
                        + "<meta property='og:description' content='su.vwv_eat on August 9, 2026: &quot;우오신 우메다점 / 오사카&quot;'>"
                        + "</head><body></body></html>").getBytes(StandardCharsets.UTF_8)));
        // when
        var result = fetcher.fetch(post + "?img_index=7&cplk=M3FxNzZ1MDN2bjdh");
        // then
        assertThat(result.text()).isEqualTo("우오신 우메다점 / 오사카");
        verify(web).fetch(eq(post), any());
    }

    @Test @DisplayName("og:url이 다른 게시물을 가리키면 그 링크는 실패한다.")
    void otherPostInOgUrlFails() throws Exception {
        // given
        when(web.fetch(any(), any())).thenReturn(page("<meta property='og:url' content='https://www.instagram.com/someone/p/Other_999/'>"
                + "<meta property='og:description' content='10 likes - trip: &quot;다른 글&quot;'>"));
        // when / then
        assertThatThrownBy(()->fetcher.fetch(uri.toString())).isInstanceOf(BusinessException.class);
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
