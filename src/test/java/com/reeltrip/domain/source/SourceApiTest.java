package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.source.entity.Source;
import com.reeltrip.domain.source.entity.SourceLink;
import com.reeltrip.domain.source.event.SourceAnalysisRequested;
import com.reeltrip.domain.source.repository.SourceLinkRepository;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.ai.LlmClient;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class SourceApiTest {

    private static final String URL = "https://www.youtube.com/shorts/demo?t=42";
    private static final String CARD = """
            [{"category":"FOOD","name":"하카타 잇소","address":"후쿠오카","latitude":33.5889,
            "longitude":130.4245,"imageUrl":"https://example.test/image.png","sourceTimestampSec":42,
            "city":"후쿠오카","keyPoints":["돈코츠 라멘"],"priceText":"¥960~980"}]
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SourceRepository sources;
    @Autowired SourceLinkRepository links;
    @Autowired UserRepository users;
    @Autowired PlaceRepository places;
    @Autowired JwtProvider jwt;
    @Autowired ApplicationEvents events;
    @Autowired EntityManager em;
    @MockitoBean LlmClient llm;

    User owner;
    User other;

    @BeforeEach
    void setUp() {
        owner = users.save(TestFixtures.user("source-owner@example.test"));
        other = users.save(TestFixtures.user("source-other@example.test"));
    }

    @Test
    @DisplayName("여러 링크를 대기로 저장하고 분석을 기다리지 않고 202와 접수 알림을 반환한다.")
    void acceptsLinksWithoutCallingLlm() throws Exception {
        // given
        var urls = List.of(URL, "https://www.instagram.com/reel/demo", "https://m.blog.naver.com/demo/123",
                "https://example.test/article", "https://youtu.be/another");
        // when
        long sourceId = create(urls);
        em.flush();
        em.clear();
        // then
        Source source = sources.findById(sourceId).orElseThrow();
        assertThat(source.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(source.getTitle()).isNull();
        var saved = links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(sourceId);
        assertThat(saved).extracting(SourceLink::getUrl).containsExactlyElementsOf(urls);
        assertThat(saved).extracting(SourceLink::getPlatform).containsExactly(
                Platform.YOUTUBE, Platform.INSTAGRAM, Platform.NAVER_BLOG, Platform.ETC, Platform.YOUTUBE);
        assertThat(saved).allMatch(link -> link.getStatus() == AnalysisStatus.PENDING);
        assertThat(events.stream(SourceAnalysisRequested.class).toList()).singleElement()
                .extracting(SourceAnalysisRequested::sourceId).isEqualTo(sourceId);
        assertThat(places.count()).isZero();
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("중복 링크를 같은 요청이나 새 요청으로 접수해도 별도 링크로 저장한다.")
    void duplicateLinksAreAllowed() throws Exception {
        // given / when
        long first = create(List.of(URL, URL));
        long second = create(List.of(URL));
        // then
        assertThat(second).isNotEqualTo(first);
        assertThat(links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(first)).hasSize(2);
        assertThat(links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(second)).hasSize(1);
    }

    static Stream<String> invalidBodies() {
        return Stream.of(
                "{}", "{\"urls\":null}", "{\"urls\":[]}", "{\"urls\":[null]}", "{\"urls\":[\" \"]}",
                "{\"urls\":[\"https://example.test/1\",\"https://example.test/2\",\"https://example.test/3\","
                        + "\"https://example.test/4\",\"https://example.test/5\",\"https://example.test/6\"]}",
                "{\"urls\":[\"not-a-url\"]}", "{\"urls\":[\"ftp://example.test/file\"]}",
                "{\"urls\":[\"file:///private/file\"]}", "{\"urls\":[\"https://user:password@example.test/private\"]}",
                "{\"urls\":[\"https://example.test:70000/path\"]}",
                "{\"urls\":[\"https://example.test/" + "a".repeat(2048) + "\"]}");
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    @DisplayName("잘못된 요청은 한 행도 만들지 않고 400을 반환한다.")
    void invalidRequestsDoNotPersist(String body) throws Exception {
        // given
        long sourceCount = sources.count();
        long linkCount = links.count();
        // when / then
        mvc.perform(post("/sources").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(sources.count()).isEqualTo(sourceCount);
        assertThat(links.count()).isEqualTo(linkCount);
        assertThat(events.stream(SourceAnalysisRequested.class).count()).isZero();
    }

    @Test
    @DisplayName("뒤쪽 링크가 잘못돼도 앞쪽 링크를 부분 저장하지 않는다.")
    void lateInvalidUrlRollsBackWholeRequest() throws Exception {
        // given / when / then
        mvc.perform(post("/sources").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("urls", List.of(URL, "invalid")))))
                .andExpect(status().isBadRequest());
        assertThat(sources.count()).isZero();
        assertThat(links.count()).isZero();
    }

    @Test
    @DisplayName("접수 직후에는 제목 null과 링크별 대기 상태 및 빈 카드 목록을 반환한다.")
    void pendingResponseKeepsNullableTitle() throws Exception {
        // given
        long sourceId = create(List.of(URL));
        // when
        JsonNode response = detail(sourceId);
        // then
        assertThat(response.get("title").isNull()).isTrue();
        assertThat(response.get("status").asText()).isEqualTo("PENDING");
        JsonNode link = response.get("sourceLinks").get(0);
        assertThat(link.get("status").asText()).isEqualTo("PENDING");
        assertThat(link.get("places").isEmpty()).isTrue();
        assertThat(link.has("code")).isFalse();
        assertThat(link.has("message")).isFalse();
    }

    @Test
    @DisplayName("진행 중 상태를 DB에 다시 읽어도 초안을 미리 노출하지 않는다.")
    void analyzingStateSurvivesReload() throws Exception {
        // given
        Source source = source(owner);
        SourceLink link = link(source, URL);
        source.markAnalyzing();
        link.markAnalyzing();
        em.flush();
        em.clear();
        // when / then
        JsonNode response = detail(source.getSourceId());
        assertThat(response.get("status").asText()).isEqualTo("ANALYZING");
        assertThat(response.get("sourceLinks").get(0).get("status").asText()).isEqualTo("ANALYZING");
        assertThat(response.get("sourceLinks").get(0).get("places").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("전체 성공은 원본 링크·시점과 카드 목록을 유지하며 placeId를 만들지 않는다.")
    void doneDraftsAreTypedAndBoundToTheirLink() throws Exception {
        // given
        Source source = source(owner);
        SourceLink link = link(source, URL);
        String draft = CARD.replace("\"category\"", "\"sourceLinkId\":999999,\"placeId\":111,"
                + "\"sourceUrl\":\"https://wrong.example.test\",\"category\"");
        complete(source, link, draft);
        // when
        JsonNode response = detail(source.getSourceId());
        JsonNode card = response.get("sourceLinks").get(0).get("places").get(0);
        // then
        assertThat(response.get("status").asText()).isEqualTo("DONE");
        assertThat(response.get("title").asText()).isEqualTo("후쿠오카 여행");
        assertThat(card.get("sourceLinkId").asLong()).isEqualTo(link.getSourceLinkId());
        assertThat(card.get("sourceUrl").asText()).isEqualTo(URL);
        assertThat(card.get("sourceTimestampSec").asInt()).isEqualTo(42);
        assertThat(card.get("priceText").asText()).isEqualTo("¥960~980");
        assertThat(card.has("placeId")).isFalse();
        assertThat(places.count()).isZero();
    }

    @Test
    @DisplayName("필수 정보가 비어 있어도 null을 가진 초안을 조회할 수 있다.")
    void incompleteDraftFieldsStayNull() throws Exception {
        // given
        Source source = source(owner);
        SourceLink link = link(source, URL);
        complete(source, link, "[{\"category\":\"FOOD\",\"name\":\"보완할 카드\",\"sourceTimestampSec\":42}]");
        // when
        JsonNode card = detail(source.getSourceId()).get("sourceLinks").get(0).get("places").get(0);
        // then
        assertThat(card.get("address").isNull()).isTrue();
        assertThat(card.get("latitude").isNull()).isTrue();
        assertThat(card.get("longitude").isNull()).isTrue();
        assertThat(card.get("imageUrl").isNull()).isTrue();
        assertThat(card.get("sourceTimestampSec").asInt()).isEqualTo(42);
    }

    @Test
    @DisplayName("일부 링크 실패는 성공한 카드와 실패한 원래 링크를 함께 반환한다.")
    void partialFailureKeepsSuccessfulCards() throws Exception {
        // given
        Source source = source(owner);
        SourceLink success = link(source, URL);
        SourceLink failed = link(source, "https://www.instagram.com/reel/failed");
        source.markAnalyzing();
        success.markAnalyzing();
        success.markDone(CARD);
        failed.markFailed(ErrorCode.SOURCE_FETCH_FAILED);
        source.markDone("후쿠오카 여행");
        em.flush();
        em.clear();
        // when
        JsonNode response = detail(source.getSourceId());
        // then
        assertThat(response.get("status").asText()).isEqualTo("DONE");
        assertThat(response.get("sourceLinks").get(0).get("places").size()).isEqualTo(1);
        JsonNode failure = response.get("sourceLinks").get(1);
        assertThat(failure.get("url").asText()).isEqualTo("https://www.instagram.com/reel/failed");
        assertThat(failure.get("code").asText()).isEqualTo("SOURCE_FETCH_FAILED");
        assertThat(failure.get("message").asText()).isEqualTo(ErrorCode.SOURCE_FETCH_FAILED.getMessage());
        assertThat(failure.get("places").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("전체 실패한 요청도 링크별 에러 코드와 빈 카드 목록을 반환한다.")
    void allLinksFailed() throws Exception {
        // given
        Source source = source(owner);
        SourceLink failed = link(source, URL);
        failed.markFailed(ErrorCode.AI_EXTRACTION_FAILED);
        source.markFailed("2026-11-14 수집");
        em.flush();
        em.clear();
        // when / then
        JsonNode response = detail(source.getSourceId());
        assertThat(response.get("status").asText()).isEqualTo("FAILED");
        assertThat(response.get("sourceLinks").get(0).get("code").asText()).isEqualTo("AI_EXTRACTION_FAILED");
        assertThat(response.get("sourceLinks").get(0).get("places").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("다른 회원의 수집과 없는 수집은 같은 404로 반환한다.")
    void foreignAndMissingSourcesAreIndistinguishable() throws Exception {
        // given
        Source foreign = source(other);
        link(foreign, URL);
        // when
        String foreignBody = mvc.perform(get("/sources/" + foreign.getSourceId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missingBody = mvc.perform(get("/sources/" + Long.MAX_VALUE).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        // then
        assertThat(foreignBody).isEqualTo(missingBody);
    }

    @Test
    @DisplayName("로그인하지 않으면 접수와 조회를 모두 거부하고 없는 회원은 접수할 수 없다.")
    void authenticationRequired() throws Exception {
        // given / when / then
        mvc.perform(post("/sources").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("urls", List.of(URL)))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/sources/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/sources").header("Authorization", "Bearer " + jwt.createAccessToken(Long.MAX_VALUE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("urls", List.of(URL)))))
                .andExpect(status().isUnauthorized());
        assertThat(sources.count()).isZero();
    }

    @Test
    @DisplayName("초안 조회만으로 카드를 저장하지 않고 기존 모두 저장 API로 저장할 수 있다.")
    void draftsRoundTripToExistingPlaceApi() throws Exception {
        // given
        Source source = source(owner);
        SourceLink link = link(source, URL);
        complete(source, link, CARD);
        JsonNode cards = detail(source.getSourceId()).get("sourceLinks").get(0).get("places");
        assertThat(places.count()).isZero();
        // when
        mvc.perform(post("/places").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("places", cards))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.places.length()").value(1));
        // then
        assertThat(places.findAll()).singleElement().satisfies(place -> {
            assertThat(place.getSourceLink().getSourceLinkId()).isEqualTo(link.getSourceLinkId());
            assertThat(place.getSourceTimestampSec()).isEqualTo(42);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[null]", "{}", "{invalid-private-json"})
    @DisplayName("손상된 저장 초안은 원문을 노출하지 않고 공통 500으로 반환한다.")
    void corruptStoredDraftDoesNotLeak(String json, CapturedOutput output) throws Exception {
        // given
        Source source = source(owner);
        SourceLink link = link(source, URL);
        complete(source, link, json);
        // when / then
        mvc.perform(get("/sources/" + source.getSourceId()).header("Authorization", bearer(owner)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
        assertThat(output.getAll()).doesNotContain("invalid-private-json");
    }

    @Test
    @DisplayName("종료한 분석 요청과 링크를 다시 진행 상태로 바꿀 수 없다.")
    void terminalStatesCannotBeReopened() {
        // given
        Source source = source(owner);
        SourceLink link = link(source, URL);
        complete(source, link, "[]");
        // when / then
        assertThatThrownBy(source::markAnalyzing).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(link::markAnalyzing).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> link.markFailed(ErrorCode.SOURCE_FETCH_FAILED)).isInstanceOf(IllegalStateException.class);
    }

    private long create(List<String> urls) throws Exception {
        String response = mvc.perform(post("/sources").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("urls", urls))))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("sourceId").asLong();
    }

    private JsonNode detail(Long sourceId) throws Exception {
        String response = mvc.perform(get("/sources/" + sourceId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private Source source(User user) {
        return sources.save(Source.create(user));
    }

    private SourceLink link(Source source, String url) {
        return links.save(SourceLink.create(source, url, Platform.YOUTUBE));
    }

    private void complete(Source source, SourceLink link, String json) {
        source.markAnalyzing();
        link.markAnalyzing();
        link.markDone(json);
        source.markDone("후쿠오카 여행");
        em.flush();
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getUserId());
    }
}
