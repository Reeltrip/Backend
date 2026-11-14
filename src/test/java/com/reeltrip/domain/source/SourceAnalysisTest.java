package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.reeltrip.domain.source.entity.*;
import com.reeltrip.domain.source.repository.*;
import com.reeltrip.domain.source.service.*;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.*;
import com.reeltrip.infra.fetcher.*;
import com.reeltrip.infra.map.PlacesClient;
import com.reeltrip.support.TestFixtures;

@SpringBootTest
class SourceAnalysisTest {
    @Autowired AnalysisStateService state;
    @Autowired SourceRepository sources;
    @Autowired SourceLinkRepository links;
    @Autowired UserRepository users;
    @Autowired PlaceRepository places;
    @MockitoBean Clock clock;
    private final Instant now=Instant.parse("2026-11-14T03:00:00Z");
    private final FetchedContent content=new FetchedContent(Platform.YOUTUBE,"https://youtu.be/abcDEF12345",null,null);

    @BeforeEach void time() { when(clock.instant()).thenReturn(now); when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul")); }

    @Test @DisplayName("일부 링크만 성공해도 초안·시점을 보존하고 전체를 완료하며 카드 저장은 하지 않는다.")
    void aggregatesPartialSuccessAndIgnoresLateCompletion() {
        // given
        var source=create(2); var children=children(source); long count=places.count();
        state.claim(children.get(0).getSourceLinkId());
        var drafts=new PlaceEnrichmentService(mock(PlacesClient.class),false)
                .enrich(children.get(0).getSourceLinkId(),content,new FakeLlmClient().extractCards(content));
        // when
        state.complete(children.get(0).getSourceLinkId(),drafts);
        state.fail(children.get(1).getSourceLinkId(),ErrorCode.SOURCE_FETCH_FAILED);
        state.complete(children.get(1).getSourceLinkId(),drafts);
        // then
        var done=sources.findById(source.getSourceId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(AnalysisStatus.DONE); assertThat(done.getTitle()).isEqualTo("후쿠오카 여행");
        assertThat(children(source).getFirst().getDraftJson()).contains("\"sourceTimestampSec\":42", "\"address\":null");
        assertThat(children(source).get(1).getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(places.count()).isEqualTo(count);
    }

    @Test @DisplayName("모든 링크가 실패하면 전체가 실패하고 접수 날짜를 제목으로 쓴다.")
    void allFailuresFinishSource() {
        // given
        var source=create(2);
        // when
        for(var link:children(source)) { state.fail(link.getSourceLinkId(),ErrorCode.SOURCE_FETCH_FAILED); }
        // then
        var failed=sources.findById(source.getSourceId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED); assertThat(failed.getTitle()).isEqualTo("2026-11-14 수집");
    }

    @Test @DisplayName("3분 전에는 유지하고 3분 지난 진행·대기 링크는 조회 복구에서 종료한다.")
    void recoversStalledLinksUsingClock() {
        // given
        var source=create(2); state.claim(children(source).getFirst().getSourceLinkId());
        Long owner=source.getUser().getUserId();
        when(clock.instant()).thenReturn(now.plusSeconds(179));
        // when / then
        state.recover(source.getSourceId(),owner);
        assertThat(sources.findById(source.getSourceId()).orElseThrow().getStatus()).isEqualTo(AnalysisStatus.ANALYZING);
        when(clock.instant()).thenReturn(now.plusSeconds(181));
        state.recover(source.getSourceId(),owner);
        assertThat(children(source)).allMatch(link->link.getStatus()==AnalysisStatus.FAILED);
        assertThat(children(source)).extracting(SourceLink::getFailCode).containsOnly("SOURCE_ANALYSIS_TIMEOUT");
    }

    @Test @DisplayName("분석 풀 포화는 영구 대기 대신 즉시 링크 실패로 집계한다.")
    void rejectsOverloadedPool() {
        // given
        var source=create(2); var fetcher=mock(SourceContentFetcher.class);
        var runner=new SourceAnalysisRunner(state,fetcher,new FakeLlmClient(),new PlaceEnrichmentService(mock(PlacesClient.class),false),
                task->{throw new RejectedExecutionException();},Duration.ofSeconds(90));
        try {
            // when
            runner.start(source.getSourceId());
            // then
            assertThat(children(source)).extracting(SourceLink::getFailCode).containsOnly("SOURCE_ANALYSIS_BUSY");
            verifyNoInteractions(fetcher);
        } finally { runner.shutdown(); }
    }

    @Test @DisplayName("링크 기한이 지나면 작업을 취소하고 뒤늦은 성공이 실패를 덮지 못한다.")
    void cancelsAndRejectsLateResult() throws Exception {
        // given
        var source=create(1); var fetcher=mock(SourceContentFetcher.class); when(fetcher.fetch(anyString())).thenReturn(content);
        var entered=new CountDownLatch(1);
        LlmClient blocking=input->{ entered.countDown(); try { new CountDownLatch(1).await(5,TimeUnit.SECONDS); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); } return new FakeLlmClient().extractCards(input); };
        try(var worker=Executors.newSingleThreadExecutor()) {
            var runner=new SourceAnalysisRunner(state,fetcher,blocking,new PlaceEnrichmentService(mock(PlacesClient.class),false),worker,Duration.ofSeconds(2));
            try {
                // when
                runner.start(source.getSourceId()); assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
                long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();
                while(sources.findById(source.getSourceId()).orElseThrow().getStatus()!=AnalysisStatus.FAILED && System.nanoTime()<end) { Thread.sleep(20); }
                // then
                assertThat(children(source).getFirst().getFailCode()).isEqualTo("SOURCE_ANALYSIS_TIMEOUT");
                assertThat(children(source).getFirst().getDraftJson()).isNull();
            } finally { runner.shutdown(); }
        }
    }

    private Source create(int count) {
        var owner=users.save(TestFixtures.user(UUID.randomUUID()+"@example.test"));
        var source=sources.save(Source.create(owner));
        for(int i=0;i<count;i++) { links.save(SourceLink.create(source,content.url()+"?item="+i,Platform.YOUTUBE)); }
        return source;
    }
    private List<SourceLink> children(Source source) { return links.findAllBySource_SourceIdOrderBySourceLinkIdAsc(source.getSourceId()); }
}
