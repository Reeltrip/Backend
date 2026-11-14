package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.reeltrip.domain.source.dto.SourceCreateRequest;
import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.source.service.*;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.FakeLlmClient;
import com.reeltrip.infra.fetcher.FetchedContent;
import com.reeltrip.support.TestFixtures;

@SpringBootTest(properties = {
        "app.analysis.enabled=false", "spring.datasource.url=${REELTRIP_ANALYSIS_MYSQL_URL}",
        "spring.datasource.username=root", "spring.datasource.password=",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "REELTRIP_ANALYSIS_MYSQL_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reeltrip_analysis.*")
class AnalysisMySqlTest {
    @Autowired SourceService service;
    @Autowired AnalysisStateService state;
    @Autowired UserRepository users;

    @Test
    @DisplayName("MySQL REPEATABLE READ에서 동시 링크 완료·부분 실패의 마지막 집계가 유실되지 않는다.")
    void concurrentFinalAggregation() throws Exception {
        var owner = users.save(TestFixtures.user(UUID.randomUUID() + "@example.test"));
        var drafts = new FakeLlmClient().extractCards(new FetchedContent(Platform.YOUTUBE,
                "https://youtu.be/abcDEF12345", null, null)).cards();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 6; round++) {
                var receipt = service.createSource(owner.getUserId(), new SourceCreateRequest(List.of(
                        "https://youtu.be/abcDEF12345", "https://youtu.be/abcDEF12346")));
                var ids = state.pending(receipt.sourceId());
                ids.forEach(state::claim);
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                boolean partial = round % 2 == 0;
                Future<?> left = pool.submit(() -> { await(ready, start); state.complete(ids.getFirst(), drafts); });
                Future<?> right = pool.submit(() -> {
                    await(ready, start);
                    if (partial) { state.fail(ids.getLast(), ErrorCode.SOURCE_FETCH_FAILED); }
                    else { state.complete(ids.getLast(), drafts); }
                });
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                left.get(10, TimeUnit.SECONDS);
                right.get(10, TimeUnit.SECONDS);
                var result = service.getSource(owner.getUserId(), receipt.sourceId());
                assertThat(result.status()).isEqualTo(AnalysisStatus.DONE);
                assertThat(result.title()).isEqualTo("후쿠오카 여행");
                assertThat(result.sourceLinks()).allMatch(link -> link.status() == AnalysisStatus.DONE || link.status() == AnalysisStatus.FAILED);
                assertThat(result.sourceLinks().stream().filter(link -> link.status() == AnalysisStatus.DONE).count()).isEqualTo(partial ? 1 : 2);
            }
        }
    }

    private static void await(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try { if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("start timeout"); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
