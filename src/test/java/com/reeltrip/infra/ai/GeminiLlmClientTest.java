package com.reeltrip.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.fetcher.FetchedContent;

/** 실제 Gemini 대신 가짜 서버에 src/test/resources/gemini/의 저장한 응답을 물려서 확인해요. */
class GeminiLlmClientTest {

    private static final String API_KEY = "test-key";
    private static final String MODEL = "gemini-test";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=abcDEF12345";
    private static final String BLOG_URL = "https://blog.naver.com/traveler/223000000000";

    private MockRestServiceServer server;
    private GeminiLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiLlmClient(builder, API_KEY, MODEL, new ObjectMapper(), ExtractionPrompt.load());
    }

    @Test
    @DisplayName("정상 응답이면 장소 카드와 교통 카드가 도시·원본 링크가 채워진 카드 초안이 되고, BE 1이 채울 항목은 비어 있다.")
    void extractCards_normalResponse() {
        // given: 명세 예시와 같은 Gemini 응답
        expectCall().andRespond(withSuccess(fixture("normal.json"), MediaType.APPLICATION_JSON));

        // when: 유튜브 링크에서 카드를 뽑으면
        ExtractionResult result = client.extractCards(youtube(YOUTUBE_URL));

        // then: 도시와 카드 2장이 그대로 나온다
        server.verify();
        assertThat(result.city()).isEqualTo("후쿠오카");
        assertThat(result.cards()).hasSize(2);

        PlaceDraft food = result.cards().get(0);
        assertThat(food.category()).isEqualTo(Category.FOOD);
        assertThat(food.name()).isEqualTo("하카타 잇소");
        assertThat(food.keyPoints()).containsExactly("돈코츠 라멘");
        assertThat(food.priceText()).isEqualTo("¥960~980");
        assertThat(food.accessText()).isEqualTo("하카타역 동쪽 도보 약 6~7분");
        assertThat(food.sourceTimestampSec()).isEqualTo(42);
        assertThat(food.memo()).isNull();
        assertThat(food.city()).isEqualTo("후쿠오카");
        assertThat(food.sourceUrl()).isEqualTo(YOUTUBE_URL);
        assertThat(food.subType()).isNull();
        assertThat(food.sourceLinkId()).isNull();
        assertThat(food.address()).isNull();
        assertThat(food.latitude()).isNull();
        assertThat(food.longitude()).isNull();
        assertThat(food.googlePlaceId()).isNull();
        assertThat(food.imageUrl()).isNull();
        assertThat(food.reviewCount()).isNull();
        assertThat(food.openingHours()).isNull();

        PlaceDraft transport = result.cards().get(1);
        assertThat(transport.category()).isEqualTo(Category.TRANSPORT);
        assertThat(transport.name()).isEqualTo("지하철 1일권");
        assertThat(transport.subType()).isEqualTo("교통패스");
        assertThat(transport.applicableRegion()).isEqualTo("후쿠오카");
        assertThat(transport.cost()).isEqualTo("640엔");
        assertThat(transport.benefit()).isNull();
        assertThat(transport.keyPoints()).isEmpty();
        assertThat(transport.city()).isEqualTo("후쿠오카");
        assertThat(transport.sourceUrl()).isEqualTo(YOUTUBE_URL);
    }

    @Test
    @DisplayName("AI가 일부 항목을 못 채워도 카드는 만들고, 빈 항목은 null, 핵심 포인트는 3개·글자 수는 저장 한도까지 자른다.")
    void extractCards_partialNull() {
        // given: city가 null이고 항목이 빠지거나 긴 응답 (코드 블록 표시도 붙어 있음)
        expectCall().andRespond(withSuccess(fixture("partial-null.json"), MediaType.APPLICATION_JSON));

        // when
        ExtractionResult result = client.extractCards(youtube(YOUTUBE_URL));

        // then
        assertThat(result.city()).isNull();
        assertThat(result.cards()).hasSize(3);

        PlaceDraft cafe = result.cards().get(0);
        assertThat(cafe.name()).isEqualTo("커피 맨션");
        assertThat(cafe.keyPoints()).containsExactly("라테", "치즈케이크", "푸딩");
        assertThat(cafe.priceText()).isNull();
        assertThat(cafe.accessText()).isNull();
        assertThat(cafe.sourceTimestampSec()).isEqualTo(83);
        assertThat(cafe.memo()).hasSizeLessThanOrEqualTo(ExtractionResponseParser.MAX_MEMO_LENGTH);
        assertThat(cafe.city()).isNull();

        PlaceDraft park = result.cards().get(1);
        assertThat(park.name()).hasSizeLessThanOrEqualTo(ExtractionResponseParser.MAX_NAME_LENGTH).startsWith("오호리 공원");
        assertThat(park.keyPoints()).isEmpty();
        assertThat(park.sourceTimestampSec()).isNull();
        assertThat(park.memo()).isNull();

        // 교통·팁 카드에는 장소 카드 항목을 넣지 않는다
        PlaceDraft tip = result.cards().get(2);
        assertThat(tip.category()).isEqualTo(Category.TIP);
        assertThat(tip.subType()).isEqualTo("환전");
        assertThat(tip.applicableRegion()).isNull();
        assertThat(tip.keyPoints()).isEmpty();
        assertThat(tip.priceText()).isNull();
    }

    @Test
    @DisplayName("카테고리가 enum 키 8개가 아니거나 이름이 빈 카드는 버리고 나머지 카드만 돌려준다.")
    void extractCards_dropUnknownCategoryAndBlankName() {
        // given: 모르는 카테고리, 소문자 카테고리, 빈 이름, 카드가 아닌 값이 섞인 응답
        expectCall().andRespond(withSuccess(fixture("unknown-category.json"), MediaType.APPLICATION_JSON));

        // when
        ExtractionResult result = client.extractCards(text(BLOG_URL, "도톤보리 쇼핑 후기"));

        // then: 정상 카드 1장만 남는다
        assertThat(result.city()).isEqualTo("오사카");
        assertThat(result.cards()).extracting(PlaceDraft::name).containsExactly("돈키호테 도톤보리점");
        assertThat(result.cards().get(0).category()).isEqualTo(Category.SHOPPING);
        // 글에서 뽑은 카드는 영상 시점이 없다
        assertThat(result.cards().get(0).sourceTimestampSec()).isNull();
        assertThat(result.cards().get(0).sourceUrl()).isEqualTo(BLOG_URL);
    }

    @Test
    @DisplayName("카드가 하나도 없으면 실패가 아니라 빈 목록을 돌려준다.")
    void extractCards_emptyCards() {
        // given
        expectCall().andRespond(withSuccess(fixture("empty-cards.json"), MediaType.APPLICATION_JSON));

        // when
        ExtractionResult result = client.extractCards(youtube(YOUTUBE_URL));

        // then
        assertThat(result.city()).isNull();
        assertThat(result.cards()).isEmpty();
    }

    @Test
    @DisplayName("응답이 JSON이 아니면 한 번 다시 부르고, 두 번째가 정상이면 카드를 돌려준다.")
    void extractCards_malformedThenNormal() {
        // given: 첫 응답은 JSON이 아니고 두 번째는 정상
        expectCall().andRespond(withSuccess(fixture("malformed.json"), MediaType.APPLICATION_JSON));
        expectCall().andRespond(withSuccess(fixture("normal.json"), MediaType.APPLICATION_JSON));

        // when
        ExtractionResult result = client.extractCards(youtube(YOUTUBE_URL));

        // then: 두 번 불렀고 카드가 나온다
        server.verify();
        assertThat(result.cards()).hasSize(2);
    }

    @Test
    @DisplayName("형식이 깨진 응답이 두 번 오면 두 번만 부르고 AI 추출 실패로 끝난다.")
    void extractCards_malformedTwice_fails() {
        // given: JSON이 아닌 응답, cards가 없는 응답
        expectCall().andRespond(withSuccess(fixture("malformed.json"), MediaType.APPLICATION_JSON));
        expectCall().andRespond(withSuccess(fixture("missing-cards.json"), MediaType.APPLICATION_JSON));

        // when / then
        assertAiExtractionFailed(youtube(YOUTUBE_URL));
        server.verify();
    }

    @Test
    @DisplayName("Gemini 응답 자체가 JSON이 아니어도 형식 깨짐으로 보고 한 번만 다시 부른다.")
    void extractCards_envelopeNotJson_retriesOnce() {
        // given
        expectCall().andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));
        expectCall().andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when / then
        assertAiExtractionFailed(youtube(YOUTUBE_URL));
        server.verify();
    }

    @Test
    @DisplayName("시간 초과면 다시 부르지 않고 AI 추출 실패로 끝난다.")
    void extractCards_timeout_fails() {
        // given: 읽기 시간 초과
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        // when / then
        assertAiExtractionFailed(youtube(YOUTUBE_URL));
        server.verify();
    }

    @Test
    @DisplayName("Gemini가 5xx를 주면 다시 부르지 않고 AI 추출 실패로 끝난다.")
    void extractCards_serverError_fails() {
        // given
        expectCall().andRespond(withServerError());

        // when / then
        assertAiExtractionFailed(youtube(YOUTUBE_URL));
        server.verify();
    }

    @Test
    @DisplayName("Gemini가 4xx(사용량 초과 등)를 주면 AI 추출 실패로 끝난다.")
    void extractCards_clientError_fails() {
        // given
        expectCall().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when / then
        assertAiExtractionFailed(youtube(YOUTUBE_URL));
        server.verify();
    }

    @Test
    @DisplayName("유튜브가 아닌 링크에 가져온 본문이 없으면 Gemini를 부르지 않고 AI 추출 실패로 끝난다.")
    void extractCards_blankTextWithoutVideo_failsWithoutCall() {
        // given: 기대하는 호출 없음

        // when / then
        assertAiExtractionFailed(new FetchedContent(Platform.INSTAGRAM, "https://www.instagram.com/reel/xxxx", "  ", null));
        assertAiExtractionFailed(new FetchedContent(Platform.NAVER_BLOG, BLOG_URL, null, null));
        server.verify();
    }

    @Test
    @DisplayName("유튜브 링크는 영상 주소를 file_uri로 넘기고, 키는 주소가 아니라 헤더로 보낸다.")
    void extractCards_youtubeRequestBody() {
        // given
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(GeminiLlmClient.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.contents[0].parts[0].file_data.file_uri").value(YOUTUBE_URL))
                .andExpect(jsonPath("$.contents[0].parts[1].text").isString())
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text",
                        containsString("FOOD (맛집): 장소 카드, 핵심 포인트 = 대표메뉴")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text", containsString("TIP (팁): 교통·팁 카드")))
                .andRespond(withSuccess(fixture("normal.json"), MediaType.APPLICATION_JSON));

        // when
        client.extractCards(youtube(YOUTUBE_URL));

        // then
        server.verify();
    }

    @Test
    @DisplayName("유튜브 쇼츠 주소는 watch 주소로 바꿔 file_uri로 넘긴다.")
    void extractCards_youtubeShortsUrl() {
        // given
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andExpect(jsonPath("$.contents[0].parts[0].file_data.file_uri").value(YOUTUBE_URL))
                .andRespond(withSuccess(fixture("normal.json"), MediaType.APPLICATION_JSON));

        // when
        ExtractionResult result = client.extractCards(youtube("https://youtube.com/shorts/abcDEF12345?feature=share"));

        // then: 카드의 원본 링크는 사용자가 넣은 주소 그대로다
        server.verify();
        assertThat(result.cards().get(0).sourceUrl()).isEqualTo("https://youtube.com/shorts/abcDEF12345?feature=share");
    }

    @Test
    @DisplayName("유튜브가 아닌 링크는 가져온 본문을 text로 넘기고 영상은 넘기지 않는다.")
    void extractCards_textRequestBody() {
        // given
        server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andExpect(jsonPath("$.contents[0].parts.length()").value(1))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("나카스 포장마차 거리 후기")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString(BLOG_URL)))
                .andExpect(jsonPath("$.contents[0].parts[0].file_data").doesNotExist())
                .andRespond(withSuccess(fixture("normal.json"), MediaType.APPLICATION_JSON));

        // when
        ExtractionResult result = client.extractCards(text(BLOG_URL, "나카스 포장마차 거리 후기"));

        // then: 글에서 뽑은 카드는 영상 시점을 버린다
        server.verify();
        assertThat(result.cards().get(0).sourceTimestampSec()).isNull();
    }

    private ResponseActions expectCall() {
        return server.expect(ExpectedCount.once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(GeminiLlmClient.API_KEY_HEADER, API_KEY));
    }

    private void assertAiExtractionFailed(FetchedContent content) {
        assertThatThrownBy(() -> client.extractCards(content))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_EXTRACTION_FAILED);
    }

    private static FetchedContent youtube(String url) {
        return new FetchedContent(Platform.YOUTUBE, url, null, "https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg");
    }

    private static FetchedContent text(String url, String body) {
        return new FetchedContent(Platform.NAVER_BLOG, url, body, null);
    }

    private static ClassPathResource fixture(String name) {
        return new ClassPathResource("gemini/" + name);
    }
}
