package com.reeltrip.domain.plan.dto;

import java.util.List;

/** GET /plans. totalCount는 "지난 여행 15" 같은 개수 표시용으로, 페이지와 관계없이 조건에 맞는 전체 수예요. */
public record PlanListResponse(List<PlanSummaryResponse> plans, long totalCount) {
}
