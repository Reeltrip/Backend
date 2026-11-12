package com.reeltrip.domain.plan.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.plan.dto.PlanAreasResponse;
import com.reeltrip.domain.plan.dto.PlanAreasUpdateRequest;
import com.reeltrip.domain.plan.dto.PlanDayOrderRequest;
import com.reeltrip.domain.plan.dto.PlanDayOrderResponse;
import com.reeltrip.domain.plan.dto.PlanDayResponse;
import com.reeltrip.domain.plan.dto.PlanDayUpdateRequest;
import com.reeltrip.domain.plan.dto.PlanStaysResponse;
import com.reeltrip.domain.plan.dto.PlanStaysUpdateRequest;
import com.reeltrip.domain.plan.entity.PlanStatus;

/** 숙소·구역·일자 저장(노드·플랜B)·일자 순서 (S4-3, S4-4, S4-5, S4-7). 소유: plan-edit 팀원 */
@Service
public class PlanEditService {

    public PlanStaysResponse replaceStays(Long planId, Long userId, PlanStaysUpdateRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return new PlanStaysResponse(request.placeIds().stream()
                .map(placeId -> new PlanStaysResponse.Stay(3L, placeId, "오리엔탈 호텔", 34.69, 135.5))
                .toList());
    }

    public PlanAreasResponse replaceAreas(Long planId, int dayNumber, Long userId, PlanAreasUpdateRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return new PlanAreasResponse(dayNumber, request.areas().stream()
                .map(area -> new PlanAreasResponse.Area(area.name(),
                        area.latitude() == null ? null : area.latitude().doubleValue(),
                        area.longitude() == null ? null : area.longitude().doubleValue()))
                .toList());
    }

    public PlanDayResponse saveDay(Long planId, int dayNumber, Long userId, PlanDayUpdateRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        List<PlanDayResponse.Node> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < request.nodes().size(); i++) {
            PlanDayUpdateRequest.Node node = request.nodes().get(i);
            nodes.add(new PlanDayResponse.Node(301L + i, i, node.placeId(), Category.FOOD, "이치란 라멘",
                    34.668, 135.501, "https://storage.googleapis.com/fake.jpg",
                    node.alternativePlaceIdsOrEmpty().stream()
                            .map(placeId -> new PlanDayResponse.Alternative(9L, placeId, "킨류 라멘"))
                            .toList()));
        }
        return new PlanDayResponse(21L, dayNumber, LocalDate.of(2026, 11, 13).plusDays(dayNumber),
                request.isCompleted(), List.of(new PlanAreasResponse.Area("난바", 34.6659, 135.5013)),
                nodes, PlanStatus.DRAFT);
    }

    public PlanDayOrderResponse reorderDays(Long planId, Long userId, PlanDayOrderRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        List<PlanDayOrderResponse.Day> days = new java.util.ArrayList<>();
        for (int i = 0; i < request.dayNumbers().size(); i++) {
            days.add(new PlanDayOrderResponse.Day(20L + request.dayNumbers().get(i), i + 1,
                    LocalDate.of(2026, 11, 14).plusDays(i)));
        }
        return new PlanDayOrderResponse(days);
    }
}
