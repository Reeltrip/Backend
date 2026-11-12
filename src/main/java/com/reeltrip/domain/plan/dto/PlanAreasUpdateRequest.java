package com.reeltrip.domain.plan.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /plans/{planId}/days/{dayNumber}/areas (S4-4). 좌표는 FE가 GET /place-search로 얻은 값 */
public record PlanAreasUpdateRequest(
        @NotNull List<@NotNull @Valid Area> areas
) {

    public record Area(
            @NotBlank @Size(max = 100) String name,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
    ) {
    }
}
