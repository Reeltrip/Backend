package com.reeltrip.domain.plan.dto;

import java.math.BigDecimal;
import java.util.List;

import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanDayArea;

/** PUT /plans/{planId}/days/{dayNumber}/areas 응답. areas는 GET /plans/{planId}의 days[].areas와 같은 모양 */
public record PlanAreasResponse(
        int dayNumber,
        List<Area> areas
) {

    public static PlanAreasResponse from(PlanDay day) {
        return new PlanAreasResponse(day.getDayNumber(), day.getAreas().stream().map(Area::from).toList());
    }

    public record Area(
            String name,
            Double latitude,
            Double longitude
    ) {

        public static Area from(PlanDayArea area) {
            return new Area(area.getName(), toDouble(area.getLatitude()), toDouble(area.getLongitude()));
        }

        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }
    }
}
