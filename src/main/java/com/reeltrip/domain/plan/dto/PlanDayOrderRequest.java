package com.reeltrip.domain.plan.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** PATCH /plans/{planId}/days/order (S4-7). i번째(0부터)에 적힌 기존 일자가 새 (i+1)일차가 돼요. 예: [2, 1, 3, 4] */
public record PlanDayOrderRequest(
        @NotNull List<@NotNull Integer> dayNumbers
) {
}
