package com.reeltrip.domain.plan.dto;

import java.time.LocalDate;
import java.util.List;

import com.reeltrip.domain.plan.entity.PlanDay;

/** PATCH /plans/{planId}/days/order 응답. 바뀐 일자 번호·날짜를 dayNumber 순으로 */
public record PlanDayOrderResponse(
        List<Day> days
) {

    public record Day(
            Long planDayId,
            int dayNumber,
            LocalDate date
    ) {

        public static Day from(PlanDay day) {
            return new Day(day.getPlanDayId(), day.getDayNumber(), day.getDate());
        }
    }
}
