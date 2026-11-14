package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.source.entity.Source;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.ai.LlmClient;
import com.reeltrip.support.TestFixtures;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=${REELTRIP_HISTORY_MYSQL_URL}",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "REELTRIP_HISTORY_MYSQL_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reeltrip_issue17.*")
class SourceHistoryHttpMySqlTest {

    @Autowired TestRestTemplate client;
    @Autowired SourceRepository sources;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @MockitoBean LlmClient llm;

    @Test
    @DisplayName("실제 HTTP·MySQL에서 수집 목록의 동률 정렬·페이지·서울 시각과 권한·입력 오류를 확인한다.")
    void listsRecentSourcesThroughLiveHttp() {
        // given: 외부 분석 없이 저장된 수집 내역을 준비해요.
        var owner = users.save(TestFixtures.user(UUID.randomUUID() + "@example.test"));
        var other = users.save(TestFixtures.user(UUID.randomUUID() + "@example.test"));
        long first = sources.save(Source.create(owner)).getSourceId();
        long second = sources.save(Source.create(owner)).getSourceId();
        sources.save(Source.create(other));
        var createdAt = LocalDateTime.of(2026, 11, 14, 10, 24);
        jdbc.update("update source set created_at = ?, title = ?, status = 'DONE' where source_id = ?",
                createdAt, "오사카 여행", first);
        jdbc.update("update source set created_at = ? where source_id = ?", createdAt, second);
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.createAccessToken(owner.getUserId()));
        // when
        var response = client.exchange("/sources?size=1", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForList("""
                select column_name from information_schema.statistics
                where table_schema = database() and table_name = 'source'
                  and index_name = 'idx_source_user_created' order by seq_in_index
                """, String.class)).containsExactly("user_id", "created_at");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var item = response.getBody().path("sources").get(0);
        assertThat(item.path("sourceId").asLong()).isEqualTo(second);
        assertThat(item.get("title").isNull()).isTrue();
        assertThat(item.path("createdAt").asText()).isEqualTo("2026-11-14T10:24:00+09:00");
        var next = client.exchange("/sources?page=1&size=1", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(next.getBody().path("sources").get(0).path("sourceId").asLong()).isEqualTo(first);
        assertThat(next.getBody().path("sources").get(0).path("title").asText()).isEqualTo("오사카 여행");
        var empty = client.exchange("/sources?page=2&size=1", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(empty.getBody().path("sources").isEmpty()).isTrue();
        var invalid = client.exchange("/sources?size=101", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().path("code").asText()).isEqualTo("INVALID_INPUT");
        assertThat(client.getForEntity("/sources", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
