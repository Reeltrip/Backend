package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.reeltrip.domain.source.dto.SourceCreateRequest;
import com.reeltrip.domain.source.entity.*;
import com.reeltrip.domain.source.repository.*;
import com.reeltrip.domain.source.service.SourceService;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.infra.ai.*;
import com.reeltrip.infra.fetcher.*;
import com.reeltrip.support.TestFixtures;

@SpringBootTest(properties="app.analysis.enabled=true")
class SourceAfterCommitTest {
    @Autowired SourceService service;
    @Autowired SourceRepository sources;
    @Autowired SourceLinkRepository links;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean SourceContentFetcher fetcher;
    @MockitoBean LlmClient llm;

    @Test @DisplayName("접수가 커밋된 뒤 DB 링크를 읽어 기존 LLM 계약으로 초안을 완료한다.")
    void runsAfterCommittedReceipt() throws Exception {
        // given
        String url="https://youtu.be/abcDEF12345?test="+UUID.randomUUID();
        var owner=users.save(TestFixtures.user(UUID.randomUUID()+"@example.test"));
        when(fetcher.fetch(url)).thenAnswer(call->{
            assertThat(links.findAll()).anyMatch(link->link.getUrl().equals(url));
            return new FetchedContent(Platform.YOUTUBE,url,null,null);
        });
        when(llm.extractCards(any())).thenAnswer(call->new FakeLlmClient().extractCards(call.getArgument(0)));
        // when
        var receipt=service.createSource(owner.getUserId(),new SourceCreateRequest(List.of(url)));
        long end=System.nanoTime()+java.time.Duration.ofSeconds(5).toNanos();
        while(sources.findById(receipt.sourceId()).orElseThrow().getStatus()!=AnalysisStatus.DONE && System.nanoTime()<end) { Thread.sleep(20); }
        // then
        assertThat(service.getSource(owner.getUserId(),receipt.sourceId()).sourceLinks().getFirst().places()).hasSize(2);
        verify(fetcher,times(1)).fetch(url);
    }

    @Test @DisplayName("접수가 롤백되면 외부 수집과 LLM을 시작하지 않는다.")
    void rolledBackReceiptDoesNotRun() {
        // given
        String url="https://youtu.be/abcDEF12345?rollback="+UUID.randomUUID();
        var owner=users.save(TestFixtures.user(UUID.randomUUID()+"@example.test"));
        // when / then
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            service.createSource(owner.getUserId(),new SourceCreateRequest(List.of(url))); throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        verify(fetcher,after(200).never()).fetch(url); verifyNoInteractions(llm);
    }
}
