package com.reeltrip.domain.source.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.reeltrip.domain.source.event.SourceAnalysisRequested;

@Component
@ConditionalOnProperty(name="app.analysis.enabled",havingValue="true",matchIfMissing=true)
public class SourceAnalysisListener {
    private final SourceAnalysisRunner runner;
    public SourceAnalysisListener(SourceAnalysisRunner runner) { this.runner=runner; }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void accepted(SourceAnalysisRequested event) { runner.start(event.sourceId()); }
}
