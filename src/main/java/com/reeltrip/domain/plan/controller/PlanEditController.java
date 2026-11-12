package com.reeltrip.domain.plan.controller;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.plan.dto.PlanAreasResponse;
import com.reeltrip.domain.plan.dto.PlanAreasUpdateRequest;
import com.reeltrip.domain.plan.dto.PlanDayOrderRequest;
import com.reeltrip.domain.plan.dto.PlanDayOrderResponse;
import com.reeltrip.domain.plan.dto.PlanDayResponse;
import com.reeltrip.domain.plan.dto.PlanDayUpdateRequest;
import com.reeltrip.domain.plan.dto.PlanStaysResponse;
import com.reeltrip.domain.plan.dto.PlanStaysUpdateRequest;
import com.reeltrip.domain.plan.service.PlanEditService;
import com.reeltrip.global.security.LoginUser;

import jakarta.validation.Valid;

/** PUT /plans/{planId}/stays, PUT /plans/{planId}/days/{dayNumber}/areas, PUT /plans/{planId}/days/{dayNumber}, PATCH /plans/{planId}/days/order. 소유: plan-edit 팀원 */
@RestController
public class PlanEditController {

    private final PlanEditService planEditService;

    public PlanEditController(PlanEditService planEditService) {
        this.planEditService = planEditService;
    }

    /** S4-3 숙소 선택 */
    @PutMapping("/plans/{planId}/stays")
    public PlanStaysResponse replaceStays(@LoginUser Long userId, @PathVariable Long planId,
                                          @Valid @RequestBody PlanStaysUpdateRequest request) {
        return planEditService.replaceStays(planId, userId, request);
    }

    /** S4-4 구역 선택 */
    @PutMapping("/plans/{planId}/days/{dayNumber}/areas")
    public PlanAreasResponse replaceAreas(@LoginUser Long userId, @PathVariable Long planId,
                                          @PathVariable int dayNumber,
                                          @Valid @RequestBody PlanAreasUpdateRequest request) {
        return planEditService.replaceAreas(planId, dayNumber, userId, request);
    }

    /** S4-5 동선 짜기: 그 날의 노드 순서·플랜B·완성 여부 저장 */
    @PutMapping("/plans/{planId}/days/{dayNumber}")
    public PlanDayResponse saveDay(@LoginUser Long userId, @PathVariable Long planId,
                                   @PathVariable int dayNumber,
                                   @Valid @RequestBody PlanDayUpdateRequest request) {
        return planEditService.saveDay(planId, dayNumber, userId, request);
    }

    /** S4-7 일자 순서 바꾸기 */
    @PatchMapping("/plans/{planId}/days/order")
    public PlanDayOrderResponse reorderDays(@LoginUser Long userId, @PathVariable Long planId,
                                            @Valid @RequestBody PlanDayOrderRequest request) {
        return planEditService.reorderDays(planId, userId, request);
    }
}
