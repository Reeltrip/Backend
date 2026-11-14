package com.reeltrip.domain.source.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.reeltrip.global.config.AsyncConfig;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.LlmClient;
import com.reeltrip.infra.fetcher.SourceContentFetcher;
import jakarta.annotation.PreDestroy;

@Component
public class SourceAnalysisRunner {
    private final AnalysisStateService state;
    private final SourceContentFetcher fetcher;
    private final LlmClient llm;
    private final PlaceEnrichmentService enrichment;
    private final Executor workers;
    private final Duration timeout;
    private final ConcurrentHashMap<Long,FutureTask<Void>> jobs=new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,Thread.ofPlatform().daemon().name("analysis-timeout-",0).factory());

    public SourceAnalysisRunner(AnalysisStateService state,SourceContentFetcher fetcher,LlmClient llm,
            PlaceEnrichmentService enrichment,@Qualifier(AsyncConfig.ANALYSIS_EXECUTOR) Executor workers,
            @Value("${app.analysis.link-timeout:PT90S}") Duration timeout) {
        if(timeout.toMillis()<1 || timeout.compareTo(Duration.ofSeconds(90))>0) { throw new IllegalArgumentException("분석 기한은 90초 이하여야 해요."); }
        this.state=state; this.fetcher=fetcher; this.llm=llm; this.enrichment=enrichment; this.workers=workers; this.timeout=timeout;
        timer.setRemoveOnCancelPolicy(true);
    }

    public void start(Long sourceId) {
        for(Long id:state.pending(sourceId)) { dispatch(id); }
    }

    private void dispatch(Long id) {
        var reference=new AtomicReference<FutureTask<Void>>();
        FutureTask<Void> job=new FutureTask<>(()->{
            try {
                var work=state.claim(id);
                if(work==null) { return null; }
                var content=fetcher.fetch(work.url());
                var result=llm.extractCards(content);
                state.complete(id,enrichment.enrich(id,content,result));
            } catch(BusinessException e) { state.fail(id,e.getErrorCode()); }
            catch(Exception e) { state.fail(id,ErrorCode.SOURCE_ANALYSIS_FAILED); }
            finally { jobs.remove(id,reference.get()); }
            return null;
        });
        reference.set(job);
        if(jobs.putIfAbsent(id,job)!=null) { return; }
        var expiry=timer.schedule(()->{
            try { state.fail(id,ErrorCode.SOURCE_ANALYSIS_TIMEOUT); }
            finally { job.cancel(true); jobs.remove(id,job); }
        },timeout.toMillis(),TimeUnit.MILLISECONDS);
        // 완료 여부와 무관하게 예약 작업을 정리해요.
        FutureTask<Void> wrapper=new FutureTask<>(()->{ try { job.run(); } finally { expiry.cancel(false); } return null; });
        try { workers.execute(wrapper); }
        catch(RuntimeException rejected) {
            expiry.cancel(false); jobs.remove(id,job); job.cancel(false);
            state.fail(id,ErrorCode.SOURCE_ANALYSIS_BUSY);
        }
    }

    @PreDestroy public void shutdown() {
        timer.shutdownNow(); jobs.values().forEach(job->job.cancel(true)); jobs.clear();
    }
}
