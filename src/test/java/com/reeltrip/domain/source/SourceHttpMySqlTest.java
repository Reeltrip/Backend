package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.source.dto.SourceCreateRequest;
import com.reeltrip.domain.source.dto.SourceCreateResponse;
import com.reeltrip.domain.source.dto.SourceDetailResponse;
import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.domain.source.event.SourceAnalysisRequested;
import com.reeltrip.domain.source.repository.SourceLinkRepository;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=${REELTRIP_SOURCE_MYSQL_URL}",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "REELTRIP_SOURCE_MYSQL_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reeltrip_issue16.*")
@Import(SourceHttpMySqlTest.ProbeConfiguration.class)
class SourceHttpMySqlTest {

    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired SourceRepository sources;
    @Autowired SourceLinkRepository links;
    @Autowired PlaceRepository places;
    @Autowired JwtProvider jwt;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CommitProbe probe;

    @Test
    @DisplayName("실제 HTTP·MySQL에서 접수 커밋 이후 링크가 보이고 저장된 부분 실패를 조회할 수 있다.")
    void liveHttpReceiptAndPolling() {
        // given
        var owner = users.save(TestFixtures.user(UUID.randomUUID() + "@example.test"));
        var other = users.save(TestFixtures.user(UUID.randomUUID() + "@example.test"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.createAccessToken(owner.getUserId()));
        var urls = List.of("https://youtu.be/demo?t=42", "https://www.instagram.com/reel/failed");
        // when
        var receipt = client.postForEntity("/sources",
                new HttpEntity<>(new SourceCreateRequest(urls), headers), SourceCreateResponse.class);
        // then
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(receipt.getBody()).isNotNull();
        long id = receipt.getBody().sourceId();
        assertThat(receipt.getBody().status()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(probe.linkCounts.get(id)).isEqualTo(2);
        var pending = client.exchange("/sources/" + id, HttpMethod.GET, new HttpEntity<>(headers), SourceDetailResponse.class);
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getBody()).isNotNull();
        assertThat(pending.getBody().sourceLinks()).extracting(link -> link.url()).containsExactlyElementsOf(urls);
        assertThat(pending.getBody().title()).isNull();

        // given: #23 분석기가 나중에 기록할 상태를 DB에 준비해요. 실제 외부 분석 호출은 없어요.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var source = sources.findById(id).orElseThrow();
            var savedLinks = links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(id);
            source.markAnalyzing();
            savedLinks.get(0).markAnalyzing();
            savedLinks.get(0).markDone("""
                    [{"category":"FOOD","name":"보완할 장소","sourceTimestampSec":42},
                    {"category":"TIP","name":"환전","subType":"환전","applicableRegion":"후쿠오카"}]
                    """);
            savedLinks.get(1).markFailed(ErrorCode.SOURCE_FETCH_FAILED);
            source.markDone("후쿠오카 여행");
        });
        // when / then
        var done = client.exchange("/sources/" + id, HttpMethod.GET, new HttpEntity<>(headers), SourceDetailResponse.class);
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        var detail = done.getBody();
        assertThat(detail).isNotNull();
        assertThat(detail.status()).isEqualTo(AnalysisStatus.DONE);
        assertThat(detail.sourceLinks().get(0).places()).hasSize(2);
        assertThat(detail.sourceLinks().get(0).places().get(0).sourceTimestampSec()).isEqualTo(42);
        assertThat(detail.sourceLinks().get(0).places().get(0).address()).isNull();
        assertThat(detail.sourceLinks().get(0).places().get(0).sourceUrl()).isEqualTo(urls.get(0));
        assertThat(detail.sourceLinks().get(1).code()).isEqualTo("SOURCE_FETCH_FAILED");
        assertThat(places.count()).isZero();

        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.setBearerAuth(jwt.createAccessToken(other.getUserId()));
        var forbidden = client.exchange("/sources/" + id, HttpMethod.GET, new HttpEntity<>(otherHeaders), String.class);
        var missing = client.exchange("/sources/" + Long.MAX_VALUE, HttpMethod.GET, new HttpEntity<>(otherHeaders), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forbidden.getBody()).isEqualTo(missing.getBody());
        assertThat(client.getForEntity("/sources/" + id, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        CommitProbe commitProbe(SourceLinkRepository links) {
            return new CommitProbe(links);
        }
    }

    static class CommitProbe {
        private final SourceLinkRepository links;
        final Map<Long, Integer> linkCounts = new ConcurrentHashMap<>();

        CommitProbe(SourceLinkRepository links) {
            this.links = links;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void record(SourceAnalysisRequested event) {
            linkCounts.put(event.sourceId(), links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(event.sourceId()).size());
        }
    }
}
