package com.reeltrip.domain.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanAlternative;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanDayArea;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.domain.plan.entity.PlanStay;

/**
 * GET /plans/{planId} (S4-7 계획 보기). PATCH /plans/{planId}도 같은 모양으로 돌려줘요.
 * role은 부른 사람의 역할이고, 로그인 안 하고 공유 계획을 보면 null이에요.
 * tips는 계획 OWNER의 같은 도시 교통·팁 카드로, 계획 지도의 "팁" 탭에서 보기만 해요.
 */
public record PlanDetailResponse(
        Long planId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String city,
        PlanStatus status,
        PlanRole role,
        List<Stay> stays,
        List<Day> days,
        List<Tip> tips) {

    public record Stay(Long planStayId, Long placeId, String name, Double latitude, Double longitude) {
    }

    public record Day(Long planDayId, int dayNumber, LocalDate date, boolean isCompleted,
                      List<Area> areas, List<Node> nodes) {
    }

    public record Area(String name, Double latitude, Double longitude) {
    }

    public record Node(Long planNodeId, int orderIndex, Long placeId, Category category, String name,
                       Double latitude, Double longitude, String imageUrl, List<Alternative> alternatives) {
    }

    public record Alternative(Long planAlternativeId, Long placeId, String name) {
    }

    public record Tip(Long placeId, Category category, String name, String imageUrl) {
    }

    public static PlanDetailResponse of(Plan plan, PlanRole role, List<Place> tipPlaces) {
        List<Stay> stays = plan.getStays().stream().map(PlanDetailResponse::toStay).toList();
        List<Day> days = plan.getDays().stream()
                .sorted(Comparator.comparingInt(PlanDay::getDayNumber))
                .map(PlanDetailResponse::toDay)
                .toList();
        List<Tip> tips = tipPlaces.stream()
                .sorted(Comparator.comparing(Place::getPlaceId))
                .map(place -> new Tip(place.getPlaceId(), place.getCategory(), place.getName(), place.getImageUrl()))
                .toList();
        return new PlanDetailResponse(plan.getPlanId(), plan.getName(), plan.getStartDate(), plan.getEndDate(),
                plan.getCity(), plan.getStatus(), role, stays, days, tips);
    }

    private static Stay toStay(PlanStay stay) {
        Place place = stay.getPlace();
        return new Stay(stay.getPlanStayId(), place.getPlaceId(), place.getName(),
                toDouble(place.getLatitude()), toDouble(place.getLongitude()));
    }

    private static Day toDay(PlanDay day) {
        List<Area> areas = day.getAreas().stream()
                .sorted(Comparator.comparingInt(PlanDayArea::getOrderIndex))
                .map(area -> new Area(area.getName(), toDouble(area.getLatitude()), toDouble(area.getLongitude())))
                .toList();
        List<Node> nodes = day.getNodes().stream()
                .sorted(Comparator.comparingInt(PlanNode::getOrderIndex))
                .map(PlanDetailResponse::toNode)
                .toList();
        return new Day(day.getPlanDayId(), day.getDayNumber(), day.getDate(), day.isCompleted(), areas, nodes);
    }

    private static Node toNode(PlanNode node) {
        Place place = node.getPlace();
        List<Alternative> alternatives = node.getAlternatives().stream()
                .map(PlanDetailResponse::toAlternative)
                .toList();
        return new Node(node.getPlanNodeId(), node.getOrderIndex(), place.getPlaceId(), place.getCategory(),
                place.getName(), toDouble(place.getLatitude()), toDouble(place.getLongitude()),
                place.getImageUrl(), alternatives);
    }

    private static Alternative toAlternative(PlanAlternative alternative) {
        Place place = alternative.getPlace();
        return new Alternative(alternative.getPlanAlternativeId(), place.getPlaceId(), place.getName());
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
