// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.domain.source.entity;

/** 링크 분석 상태. source(요청 전체)와 source_link(링크 하나)가 같이 써요. */
public enum AnalysisStatus {
    PENDING,
    ANALYZING,
    DONE,
    FAILED
}
