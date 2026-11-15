package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.domain.source.entity.Source;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.ai.LlmClient;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SourceHistoryApiTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 14, 10, 24);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SourceRepository sources;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired JwtProvider jwt;
    @MockitoBean LlmClient llm;
    @MockitoBean Clock clock;

    User owner;
    User other;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(CREATED.atZone(ZoneId.of("Asia/Seoul")).toInstant());
        owner = users.save(TestFixtures.user("history-owner@example.test"));
        other = users.save(TestFixtures.user("history-other@example.test"));
    }

    @Test
    @DisplayName("본인 수집 내역의 저장된 제목·상태와 서울 생성 시각만 최신순으로 반환한다.")
    void listsOnlyOwnedSummariesWithoutAnalysis() throws Exception {
        // given: 분석기가 저장한 상태를 준비해요. 목록은 제목을 만들지 않아요.
        long done = saved(owner, CREATED.minusDays(3), AnalysisStatus.DONE, "후쿠오카 여행");
        long failed = saved(owner, CREATED.minusDays(2), AnalysisStatus.FAILED, "분석 실패");
        long analyzing = saved(owner, CREATED.minusDays(1), AnalysisStatus.ANALYZING, null);
        long pending = saved(owner, CREATED, AnalysisStatus.PENDING, null);
        saved(other, CREATED.plusDays(1), AnalysisStatus.DONE, "다른 회원 여행");
        // when
        var body = list("");
        // then
        assertThat(ids(body)).containsExactly(pending, analyzing, failed, done);
        assertThat(body.size()).isEqualTo(1);
        var items = body.path("sources");
        assertThat(items.get(0).size()).isEqualTo(4);
        assertThat(items.get(0).has("title")).isTrue();
        assertThat(items.get(0).get("title").isNull()).isTrue();
        assertThat(items.get(0).path("createdAt").asText()).isEqualTo("2026-11-14T10:24:00+09:00");
        assertThat(items.get(0).path("status").asText()).isEqualTo("PENDING");
        assertThat(items.get(1).path("status").asText()).isEqualTo("ANALYZING");
        assertThat(items.get(2).path("status").asText()).isEqualTo("FAILED");
        assertThat(items.get(3).path("status").asText()).isEqualTo("DONE");
        assertThat(items.get(3).path("title").asText()).isEqualTo("후쿠오카 여행");
        assertThat(sources.count()).isEqualTo(5);
        assertThat(sources.findById(pending).orElseThrow().getTitle()).isNull();
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("생성 시각이 같은 수집은 ID 내림차순으로 정렬해 페이지 사이에 중복·누락되지 않는다.")
    void breaksTiesBeforePaging() throws Exception {
        // given
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            expected.addFirst(saved(owner, CREATED, AnalysisStatus.PENDING, null));
        }
        saved(other, CREATED, AnalysisStatus.PENDING, null);
        // when
        var actual = new ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            actual.addAll(ids(list("?page=" + page + "&size=2")));
        }
        // then
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("페이지를 생략하면 최신 10개를 반환하고 다음 페이지는 나머지만 반환한다.")
    void defaultsToTenAndPageZero() throws Exception {
        // given
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 13; i++) {
            expected.addFirst(saved(owner, CREATED.plusMinutes(i), AnalysisStatus.PENDING, null));
        }
        // when / then
        assertThat(ids(list(""))).containsExactlyElementsOf(expected.subList(0, 10));
        assertThat(ids(list("?page=1"))).containsExactlyElementsOf(expected.subList(10, 13));
    }

    @Test
    @DisplayName("허용하는 최대 크기 100개만 조회하고 남은 한 개는 다음 페이지에서 반환한다.")
    void acceptsMaximumSize() throws Exception {
        // given
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 101; i++) {
            expected.addFirst(saved(owner, CREATED, AnalysisStatus.PENDING, null));
        }
        // when / then
        assertThat(ids(list("?size=100"))).containsExactlyElementsOf(expected.subList(0, 100));
        assertThat(ids(list("?page=1&size=100"))).containsExactly(expected.getLast());
    }

    @Test
    @DisplayName("내 수집이 없거나 마지막 페이지를 넘으면 200과 빈 목록을 반환한다.")
    void returnsEmptyListForNoResults() throws Exception {
        // given
        saved(other, CREATED, AnalysisStatus.DONE, "다른 회원의 제목");
        // when / then
        assertThat(list("").toString()).isEqualTo("{\"sources\":[]}");
        saved(owner, CREATED, AnalysisStatus.PENDING, null);
        assertThat(list("?page=1").toString()).isEqualTo("{\"sources\":[]}");
        assertThat(list("?page=2147483647&size=1").toString()).isEqualTo("{\"sources\":[]}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=-1", "size=101", "page=abc", "size=abc",
            "page=1.5", "size=1.5", "page=2147483648", "size=2147483648",
            "page=2147483647&size=100", "page=21474837&size=100"})
    @DisplayName("페이지·크기 형식과 범위를 벗어나거나 시작 위치가 넘치면 공통 400으로 거부한다.")
    void rejectsInvalidPagination(String query) throws Exception {
        // given / when / then
        mvc.perform(get("/sources?" + query).header("Authorization", token(owner.getUserId())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("회원 ID 쿼리를 임의로 보내도 토큰 회원의 내역만 조회한다.")
    void cannotSelectAnotherOwner() throws Exception {
        // given
        long own = saved(owner, CREATED, AnalysisStatus.PENDING, null);
        saved(other, CREATED.plusMinutes(1), AnalysisStatus.PENDING, null);
        // when / then
        assertThat(ids(list("?userId=" + other.getUserId()))).containsExactly(own);
    }

    @Test
    @DisplayName("로그인하지 않았거나 토큰 회원이 존재하지 않으면 수집 목록을 조회할 수 없다.")
    void requiresExistingAuthenticatedUser() throws Exception {
        // given / when / then
        mvc.perform(get("/sources")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/sources").header("Authorization", token(Long.MAX_VALUE)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private JsonNode list(String query) throws Exception {
        var result = mvc.perform(get("/sources" + query).header("Authorization", token(owner.getUserId())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<Long> ids(JsonNode body) {
        var ids = new ArrayList<Long>();
        body.path("sources").forEach(item -> ids.add(item.path("sourceId").asLong()));
        return ids;
    }

    private String token(Long userId) {
        return "Bearer " + jwt.createAccessToken(userId);
    }

    private long saved(User user, LocalDateTime createdAt, AnalysisStatus status, String title) {
        // 실제 저장과 같은 JPA auditing으로 CI(UTC)·로컬(서울)에서 같은 시각을 준비해요.
        when(clock.instant()).thenReturn(createdAt.atZone(ZoneId.of("Asia/Seoul")).toInstant());
        long id = sources.saveAndFlush(Source.create(user)).getSourceId();
        jdbc.update("update source set status = ?, title = ? where source_id = ?", status.name(), title, id);
        em.clear();
        return id;
    }
}
