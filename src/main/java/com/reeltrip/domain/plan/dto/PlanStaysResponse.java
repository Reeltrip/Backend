package com.reeltrip.domain.plan.dto;

import java.math.BigDecimal;
import java.util.List;

import com.reeltrip.domain.plan.entity.PlanStay;

/** PUT /plans/{planId}/stays 응답. GET /plans/{planId}의 stays와 같은 모양 */
public record PlanStaysResponse(
        List<Stay> stays
) {

    public static PlanStaysResponse from(List<PlanStay> stays) {
        return new PlanStaysResponse(stays.stream().map(Stay::from).toList());
    }

    public record Stay(
            Long planStayId,
            Long placeId,
            String name,
            Double latitude,
            Double longitude
    ) {

        public static Stay from(PlanStay stay) {
            return new Stay(
                    stay.getPlanStayId(),
                    stay.getPlace().getPlaceId(),
                    stay.getPlace().getName(),
                    toDouble(stay.getPlace().getLatitude()),
                    toDouble(stay.getPlace().getLongitude()));
        }

        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }
    }
}
