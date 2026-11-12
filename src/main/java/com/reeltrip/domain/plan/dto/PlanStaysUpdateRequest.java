package com.reeltrip.domain.plan.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** PUT /plans/{planId}/stays (S4-3). 숙소 없이 건너뛰면 빈 목록 */
public record PlanStaysUpdateRequest(
        @NotNull List<@NotNull Long> placeIds
) {
}
