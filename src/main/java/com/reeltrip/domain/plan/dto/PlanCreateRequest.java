package com.reeltrip.domain.plan.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /plans (S4-1). 시작일이 종료일보다 뒤인 건 서비스가 INVALID_PLAN_PERIOD로 막아요. */
public record PlanCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 255) String cityPlaceId,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
