package com.reeltrip.infra.fetcher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.ExtractionPrompt;
import com.reeltrip.infra.ai.GeminiLlmClient;
import com.reeltrip.infra.fetcher.SourceContentFetcher;
import com.reeltrip.infra.fetcher.SourceUrlClassifier;

class WebContentFetcherTest {
    private static final URI CANONICAL = URI.create("https://blog.naver.com/PostView.naver?blogId=trip_writer&logNo=223123456789");
    private final SafeWebClient web = mock(SafeWebClient.class);
    private final HtmlContentExtractor extractor = new HtmlContentExtractor();
    private final SourceContentFetcher fetcher = new SourceContentFetcher(new SourceUrlClassifier(),
            List.of(new NaverBlogContentFetcher(web, extractor), new WebContentFetcher(web, extractor)));

    @ParameterizedTest
    @ValueSource(strings = {"https://blog.naver.com/trip_writer/223123456789",
            "https://m.blog.naver.com/trip_writer/223123456789?tracking=do-not-forward",
            "http://blog.naver.com/PostView.naver?logNo=223123456789&blogId=trip_writer&tracking=do-not-forward"})
    @DisplayName("네이버 글 주소를 검증한 PostView 주소로 바꾸고 원본 주소·본문·썸네일을 반환한다.")
    void fetchesNaverArticle(String original) throws Exception {
        // given
        when(web.fetch(eq(CANONICAL), any())).thenReturn(HtmlContentExtractorTest.fixture("naver-post.html"));
        when(web.thumbnail(anyString(), any(), any())).thenReturn("https://images.example.test/kyoto.jpg");
        // when
        var content = fetcher.fetch(original);
        // then
        assertThat(content.platform()).isEqualTo(Platform.NAVER_BLOG);
        assertThat(content.url()).isEqualTo(original);
        assertThat(content.text()).contains("교토 니시키 시장", "교토 유바 본점").doesNotContain("블로그 메뉴", "댓글", "광고");
        assertThat(content.thumbnailUrl()).endsWith("/kyoto.jpg");
        verify(web).fetch(eq(CANONICAL), any());
    }

    @Test
    @DisplayName("네이버 mainFrame은 같은 글의 검증된 본문 주소일 때 한 번만 따라간다.")
    void followsSameArticleFrame() throws Exception {
        // given
        URI frame = URI.create(CANONICAL + "&directAccess=false");
        var wrapper = HtmlContentExtractorTest.fixture("naver-wrapper.html");
        when(web.fetch(eq(CANONICAL), any())).thenReturn(new SafeWebClient.Page(CANONICAL, wrapper.contentType(), wrapper.bytes()));
        when(web.fetch(eq(frame), any())).thenReturn(HtmlContentExtractorTest.fixture("naver-old.html"));
        // when
        var content = fetcher.fetch("https://blog.naver.com/trip_writer/223123456789");
        // then
        assertThat(content.text()).contains("흰여울 문화마을");
        verify(web, times(2)).fetch(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/private", "https://evil.example/PostView.naver?blogId=trip_writer&logNo=223123456789",
            "/PostView.naver?blogId=another&logNo=223123456789", "/PostView.naver?blogId=trip_writer&logNo=1"})
    @DisplayName("내부·외부 호스트와 다른 글의 mainFrame은 추가 접속 없이 거부한다.")
    void rejectsUnsafeFrames(String target) throws Exception {
        // given
        String html = "<html><body><iframe id='mainFrame' src='" + target + "'></iframe></body></html>";
        when(web.fetch(eq(CANONICAL), any())).thenReturn(new SafeWebClient.Page(CANONICAL, "text/html",
                html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // when / then
        assertFetchFailed("https://blog.naver.com/trip_writer/223123456789");
        verify(web, times(1)).fetch(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://blog.naver.com/trip_writer", "https://blog.naver.com/trip_writer/not-a-number",
            "https://blog.naver.com/PostView.naver?blogId=x&logNo=1&logNo=2",
            "https://blog.naver.com/PostView.naver?blogId=..%2Fsecret&logNo=1"})
    @DisplayName("글 식별자가 없거나 중복·경로 주입이 있으면 요청 전에 실패한다.")
    void rejectsBadNaverUrls(String original) {
        // given / when / then
        assertFetchFailed(original);
        verifyNoInteractions(web);
    }

    @Test
    @DisplayName("일반 본문과 원본 URL은 기존 Gemini 텍스트 입력에 이어지고 영상 시점은 만들지 않는다.")
    void integratesWithExistingLlmTextContract() throws Exception {
        // given
        String original = "https://travel.example.test/article";
        when(web.fetch(any(), any())).thenReturn(HtmlContentExtractorTest.fixture("article.html"));
        when(web.thumbnail(anyString(), any(), any())).thenReturn("https://travel.example.test/images/trip.jpg");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var llm = new GeminiLlmClient(builder, "test-only-key", "test-model", new ObjectMapper(), ExtractionPrompt.load());
        server.expect(jsonPath("$.contents[0].parts[0].text", org.hamcrest.Matchers.containsString("제주 동쪽 여행")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", org.hamcrest.Matchers.containsString(original)))
                .andExpect(jsonPath("$.contents[0].parts[0].file_data").doesNotExist())
                .andRespond(withSuccess(new ClassPathResource("gemini/normal.json"), MediaType.APPLICATION_JSON));
        // when
        var content = fetcher.fetch(original);
        var result = llm.extractCards(content);
        // then
        server.verify();
        assertThat(content.platform()).isEqualTo(Platform.ETC);
        assertThat(content.thumbnailUrl()).endsWith("/trip.jpg");
        assertThat(result.cards().getFirst().sourceUrl()).isEqualTo(original);
        assertThat(result.cards().getFirst().sourceTimestampSec()).isNull();
    }

    @Test
    @DisplayName("빈 본문·네트워크 실패는 그 링크만 실패시키고 다음 링크는 계속 수집할 수 있다.")
    void isolatesFailuresAndAllowsMissingThumbnail() throws Exception {
        // given
        when(web.fetch(any(), any())).thenReturn(HtmlContentExtractorTest.fixture("empty.html"))
                .thenThrow(new java.io.IOException("network"))
                .thenReturn(HtmlContentExtractorTest.fixture("article.html"));
        // when / then
        assertFetchFailed("https://travel.example/empty");
        assertFetchFailed("https://travel.example/unavailable");
        var content = fetcher.fetch("https://travel.example/good");
        assertThat(content.text()).contains("제주 동쪽 여행");
        assertThat(content.thumbnailUrl()).isNull();
    }

    private void assertFetchFailed(String url) {
        assertThatThrownBy(() -> fetcher.fetch(url)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SOURCE_FETCH_FAILED));
    }
}
