package com.reeltrip.infra.fetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

class SourceContentFetcherTest {

    private final SourceUrlClassifier classifier = new SourceUrlClassifier();
    private final YoutubeOembedClient metadata = mock(YoutubeOembedClient.class);
    private final SourceContentFetcher fetcher = new SourceContentFetcher(classifier,
            List.of(new YoutubeContentFetcher(classifier, metadata)));

    @BeforeEach
    void setUp() {
        when(metadata.fetch("abcDEF12345")).thenReturn(Optional.of(new YoutubeOembedClient.Metadata("여행 영상",
                "https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg")));
    }

    @ParameterizedTest
    @CsvSource({"https://youtube.com/watch?v=abcDEF12345,YOUTUBE",
            "https://youtu.be/abcDEF12345,YOUTUBE", "https://WWW.YOUTUBE.COM/shorts/abcDEF12345,YOUTUBE",
            "https://www.instagram.com/reel/example,INSTAGRAM", "https://m.instagram.com/reel/example,INSTAGRAM",
            "https://blog.naver.com/trip/123,NAVER_BLOG", "https://m.blog.naver.com/trip/123,NAVER_BLOG",
            "https://example.test/article,ETC", "https://youtube.com.evil.test/watch?v=abcDEF12345,ETC",
            "https://evil.youtube.com/watch?v=abcDEF12345,ETC", "https://notinstagram.com/reel/example,ETC"})
    @DisplayName("정확한 허용 호스트로 네 플랫폼을 판별하고 비슷한 이름은 ETC로 남긴다.")
    void classifiesWithoutNetwork(String url, Platform expected) {
        // given / when / then
        assertThat(classifier.classify(url)).isEqualTo(expected);
        verifyNoInteractions(metadata);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://www.youtube.com/watch?v=abcDEF12345&t=42s",
            "https://youtube.com/watch?feature=share&v=abcDEF12345#t=42",
            "http://m.youtube.com/watch?v=abcDEF12345", "https://music.youtube.com/watch?v=abcDEF12345",
            "https://www.youtube.com/shorts/abcDEF12345?si=share-token", "https://youtube.com/shorts/abcDEF12345/",
            "https://youtu.be/abcDEF12345?t=42", "https://www.youtu.be/abcDEF12345/",
            "HTTPS://WWW.YOUTUBE.COM:443/watch?v=abcDEF12345"})
    @DisplayName("일반·쇼츠·단축 영상 URL과 타임스탬프를 그대로 넘기고 본문은 null로 유지한다.")
    void fetchesSupportedVideoUrls(String url) {
        // given / when
        var content = fetcher.fetch(url);
        // then
        assertThat(content.platform()).isEqualTo(Platform.YOUTUBE);
        assertThat(content.url()).isEqualTo(url);
        assertThat(content.text()).isNull();
        assertThat(content.thumbnailUrl()).isEqualTo("https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg");
        verify(metadata).fetch("abcDEF12345");
        verifyNoMoreInteractions(metadata);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-url", " https://youtu.be/abcDEF12345", "file:///etc/passwd",
            "ftp://youtube.com/watch?v=abcDEF12345", "https://user:secret@youtube.com/watch?v=abcDEF12345",
            "https://youtube.com:8080/watch?v=abcDEF12345", "https://youtube.com:80/watch?v=abcDEF12345",
            "https://youtube.com.evil.test/watch?v=abcDEF12345", "https://evil.youtube.com/watch?v=abcDEF12345",
            "https://youtube.com./watch?v=abcDEF12345", "https://youtu.be", "https://youtu.be/",
            "https://youtube.com/watch", "https://youtube.com/watch?v=", "https://youtu.be/tooShort",
            "https://youtube.com/watch?v=abcDEF12345&v=abcDEF12345", "https://youtube.com/watch?v=abcDEF12345&%76=other",
            "https://youtube.com/playlist?list=abcDEF12345", "https://youtube.com/redirect?q=http://127.0.0.1",
            "https://youtu.be/abcDEF12345/extra", "https://youtube.com/shorts/abcDEF12345%2fother",
            "http://127.0.0.1/private", "http://2130706433/private", "http://[::1]/private",
            "http://169.254.169.254/latest/meta-data/", "http://metadata.google.internal/",
            "https://www.instagram.com/reel/example", "https://blog.naver.com/trip/123", "https://example.test/article"})
    @DisplayName("잘못되거나 미지원인 주소와 내부 주소는 외부 호출 전에 링크 수집 실패로 거부한다.")
    void rejectsWithoutRequest(String url) {
        // given / when / then
        assertThatThrownBy(() -> fetcher.fetch(url)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.SOURCE_FETCH_FAILED));
        verifyNoInteractions(metadata);
    }

    @ParameterizedTest
    @ValueSource(ints = {2049, 10000})
    @DisplayName("2048자를 넘는 주소는 접속 전에 거부한다.")
    void rejectsLongUrls(int length) {
        // given
        String url = "https://youtu.be/abcDEF12345?data=" + "x".repeat(length);
        // when / then
        assertThatThrownBy(() -> fetcher.fetch(url)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(metadata);
    }
}
