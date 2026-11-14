package com.reeltrip.domain.source.event;

/** #23 분석 흐름에서 AFTER_COMMIT으로 받아 처리할 접수 알림이에요. */
public record SourceAnalysisRequested(Long sourceId) { }
