package com.reeltrip.domain.source.dto;

import java.util.List;

/** S1 링크 홈의 최근 수집 내역. */
public record SourceListResponse(List<SourceSummaryResponse> sources) {
}
