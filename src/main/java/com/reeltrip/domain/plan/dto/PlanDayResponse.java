package com.reeltrip.domain.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.plan.entity.PlanAlternative;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanStatus;

/**
 * PUT /plans/{planId}/days/{dayNumber} 응답. GET /plans/{planId}의 days[] 항목 한 개와 같은 모양에
 * 저장 뒤 계획 상태(planStatus)를 붙였어요. 모든 일자가 완성되면 COMPLETED예요.
 */
public record PlanDayResponse(
        Long planDayId,
        int dayNumber,
        LocalDate date,
        boolean isCompleted,
        List<PlanAreasResponse.Area> areas,
        List<Node> nodes,
        PlanStatus planStatus
) {

    public static PlanDayResponse of(PlanDay day, PlanStatus planStatus) {
        return new PlanDayResponse(
                day.getPlanDayId(),
                day.getDayNumber(),
                day.getDate(),
                day.isCompleted(),
                day.getAreas().stream().map(PlanAreasResponse.Area::from).toList(),
                day.getNodes().stream()
                        .sorted(Comparator.comparingInt(PlanNode::getOrderIndex))
                        .map(Node::from)
                        .toList(),
                planStatus);
    }

    public record Node(
            Long planNodeId,
            int orderIndex,
            Long placeId,
            Category category,
            String name,
            Double latitude,
            Double longitude,
            String imageUrl,
            List<Alternative> alternatives
    ) {

        public static Node from(PlanNode node) {
            Place place = node.getPlace();
            return new Node(
                    node.getPlanNodeId(),
                    node.getOrderIndex(),
                    place.getPlaceId(),
                    place.getCategory(),
                    place.getName(),
                    toDouble(place.getLatitude()),
                    toDouble(place.getLongitude()),
                    place.getImageUrl(),
                    node.getAlternatives().stream().map(Alternative::from).toList());
        }

        private static Double toDouble(BigDecimal value) {
            return value == null ? null : value.doubleValue();
        }
    }

    public record Alternative(
            Long planAlternativeId,
            Long placeId,
            String name
    ) {

        public static Alternative from(PlanAlternative alternative) {
            return new Alternative(
                    alternative.getPlanAlternativeId(),
                    alternative.getPlace().getPlaceId(),
                    alternative.getPlace().getName());
        }
    }
}
