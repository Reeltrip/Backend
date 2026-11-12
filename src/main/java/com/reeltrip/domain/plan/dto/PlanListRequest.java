package com.reeltrip.domain.plan.dto;

import com.reeltrip.domain.plan.entity.PlanStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * GET /plans 쿼리 (S4-0, S5-1).
 * owner: me(내가 OWNER) / shared(OWNER가 아닌 멤버) / 생략(둘 다)
 * period: UPCOMING(종료일이 오늘이거나 그 뒤) / PAST(종료일이 오늘 전) / 생략(전체)
 */
public record PlanListRequest(
        @Pattern(regexp = "me|shared") String owner,
        PlanStatus status,
        @Pattern(regexp = "UPCOMING|PAST") String period,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Pattern(regexp = "LATEST") String sortType) {

    public static final int DEFAULT_SIZE = 20;

    public PlanListRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
        if (sortType == null) {
            sortType = "LATEST";
        }
    }
}
