package com.reeltrip.infra.fetcher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class HtmlContentExtractorTest {
    private final HtmlContentExtractor extractor = new HtmlContentExtractor();

    @Test
    @DisplayName("저장한 일반 HTML에서 본문을 고르고 메뉴·스크립트·댓글·숨은 글을 뺀다.")
    void extractsArticleWithoutNavigation() throws Exception {
        // given
        var page = fixture("article.html");
        // when
        var result = extractor.extract(page, false);
        // then
        assertThat(result.text()).isEqualTo("제주 동쪽 여행 성산 일출봉 & 섭지코지를 하루에 둘러봤어요. 해변 카페에서 바다를 볼 수 있어요.");
        assertThat(result.thumbnail()).isEqualTo("/images/trip.jpg");
    }

    @Test
    @DisplayName("네이버 신·구 편집기의 본문과 mainFrame 주소를 저장한 HTML에서 읽는다.")
    void extractsNaverArticleAndFrame() throws Exception {
        // given / when / then
        assertThat(extractor.extract(fixture("naver-post.html"), true).text())
                .isEqualTo("교토 니시키 시장에서 유바 고로케를 먹었어요. 가게 이름은 교토 유바 본점이고 오전 10시에 문을 열어요.");
        assertThat(extractor.extract(fixture("naver-old.html"), true).text()).contains("부산 영도 흰여울", "해안 산책로");
        var wrapper = extractor.extract(fixture("naver-wrapper.html"), true);
        assertThat(wrapper.text()).isEmpty();
        assertThat(wrapper.mainFrame()).isEqualTo("/PostView.naver?blogId=trip_writer&logNo=223123456789&directAccess=false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty.html", "naver-wrapper.html"})
    @DisplayName("메뉴·스크립트·폼·프레임만 있는 HTML은 본문이 비어 있다.")
    void ignoresNonContentOnlyPages(String name) throws Exception {
        // given / when / then
        assertThat(extractor.extract(fixture(name), false).text()).isEmpty();
    }

    @Test
    @DisplayName("HTTP·meta·BOM 문자셋을 우선순위대로 적용하고 잘못된 인코딩은 거부한다.")
    void decodesDeclaredCharsets() throws Exception {
        // given
        String html = "<html><body><div id='content'>부산 여행 기록</div></body></html>";
        byte[] korean = html.getBytes(Charset.forName("EUC-KR"));
        // when / then
        assertThat(extractor.extract(page(korean, "text/html; charset=EUC-KR"), false).text()).isEqualTo("부산 여행 기록");
        String meta = "<html><head><meta charset='EUC-KR'></head><body>부산 여행 기록</body></html>";
        assertThat(extractor.extract(page(meta.getBytes(Charset.forName("EUC-KR")), "text/html"), false).text())
                .isEqualTo("부산 여행 기록");
        byte[] bom = ("\ufeff" + html).getBytes(StandardCharsets.UTF_8);
        assertThat(extractor.extract(page(bom, "text/html; charset=ASCII"), false).text()).isEqualTo("부산 여행 기록");
        assertThatThrownBy(() -> extractor.extract(page(korean, "text/html; charset=no-such-charset"), false))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> extractor.extract(page(new byte[]{(byte) 0xff}, "text/html; charset=UTF-8"), false))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    @DisplayName("HTML이 덜 닫혀 있어도 정적 본문을 읽고 긴 본문은 30000자까지 자른다.")
    void recoversAndBoundsText() throws Exception {
        // given
        String html = "<html><body><div id='content'><p>" + "여행".repeat(20_000);
        // when
        String text = extractor.extract(page(html.getBytes(StandardCharsets.UTF_8), "text/html"), false).text();
        // then
        assertThat(text).hasSize(30_000).startsWith("여행여행");
    }

    @Test
    @DisplayName("body의 CSS·스크립트와 닫히지 않은 원문 블록을 제외하고 유니코드 앞에서도 위치가 어긋나지 않는다.")
    void removesRawTextWithoutCorruptingPositions() throws Exception {
        // given
        String html = "<html><body>İ 여행 < 3 <style data-x='>'>body { color:red }</style>"
                + "<p>부산 산책</p><script>doSomething()</script></body></html>";
        // when / then
        assertThat(extractor.extract(page(html.getBytes(StandardCharsets.UTF_8), "text/html"), false).text())
                .contains("부산 산책").doesNotContain("color", "doSomething");
        String unclosed = "<html><body><p>부산 산책</p><style>감출 CSS";
        assertThat(extractor.extract(page(unclosed.getBytes(StandardCharsets.UTF_8), "text/html"), false).text())
                .isEqualTo("부산 산책");
    }

    @Test
    @DisplayName("노드 수나 중첩 깊이를 넘은 HTML은 부분 본문을 반환하지 않는다.")
    void rejectsComplexHtml() {
        // given / when / then
        String many = "<html><body>" + "<div>여행</div>".repeat(20_001) + "</body></html>";
        assertThatThrownBy(() -> extractor.extract(page(many.getBytes(StandardCharsets.UTF_8), "text/html"), false))
                .isInstanceOf(java.io.IOException.class);
        String deep = "<html><body>" + "<div>".repeat(140) + "여행" + "</div>".repeat(140) + "</body></html>";
        assertThatThrownBy(() -> extractor.extract(page(deep.getBytes(StandardCharsets.UTF_8), "text/html"), false))
                .isInstanceOf(java.io.IOException.class);
    }

    static SafeWebClient.Page fixture(String name) throws Exception {
        return page(new ClassPathResource("fetcher/" + name).getContentAsByteArray(), "text/html; charset=UTF-8");
    }

    static SafeWebClient.Page page(byte[] body, String type) {
        return new SafeWebClient.Page(URI.create("https://travel.example.test/article"), type, body);
    }
}
