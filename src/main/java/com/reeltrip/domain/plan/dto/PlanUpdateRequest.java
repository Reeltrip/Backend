package com.reeltrip.domain.plan.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /plans/{planId} (Figma 계획 편집). 온 값만 바꿔요.
 * city를 보내면 cityPlaceId·latitude·longitude도 그 도시 값으로 같이 바꿔요(안 보낸 건 null).
 */
public record PlanUpdateRequest(
        @Size(min = 1, max = 50) @Pattern(regexp = "(?s).*\\S.*") String name,
        LocalDate startDate,
        LocalDate endDate,
        @Size(min = 1, max = 100) @Pattern(regexp = "(?s).*\\S.*") String city,
        @Size(max = 255) String cityPlaceId,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
