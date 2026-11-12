package com.reeltrip.domain.plan.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.plan.dto.PlanCopyResponse;
import com.reeltrip.domain.plan.dto.PlanCreateRequest;
import com.reeltrip.domain.plan.dto.PlanCreateResponse;
import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.domain.plan.dto.PlanListRequest;
import com.reeltrip.domain.plan.dto.PlanListResponse;
import com.reeltrip.domain.plan.dto.PlanUpdateRequest;
import com.reeltrip.domain.plan.service.PlanService;
import com.reeltrip.global.security.LoginUser;

import jakarta.validation.Valid;

/** GET/POST /plans, GET/PATCH/DELETE /plans/{planId}, POST /plans/{planId}/copies. 소유: plan 팀원 */
@RestController
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /** S4-0 계획 홈(status=DRAFT), S5-1 여행목록(owner·period) */
    @GetMapping("/plans")
    public PlanListResponse getPlans(@LoginUser Long userId, @Valid @ModelAttribute PlanListRequest request) {
        return planService.getPlans(userId, request);
    }

    /** S4-1·S4-2 계획 만들기 */
    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanCreateResponse createPlan(@LoginUser Long userId, @Valid @RequestBody PlanCreateRequest request) {
        return planService.createPlan(userId, request);
    }

    /** S4-7 계획 보기. 공유가 켜진 계획은 로그인하지 않아도 볼 수 있어요. */
    @GetMapping("/plans/{planId}")
    public PlanDetailResponse getPlan(@LoginUser(required = false) Long userId, @PathVariable Long planId) {
        return planService.getPlan(planId, userId);
    }

    /** Figma 계획 편집 */
    @PatchMapping("/plans/{planId}")
    public PlanDetailResponse modifyPlan(@LoginUser Long userId, @PathVariable Long planId,
                                         @Valid @RequestBody PlanUpdateRequest request) {
        return planService.modifyPlan(planId, userId, request);
    }

    /** S5-1 계획 삭제. OWNER만 */
    @DeleteMapping("/plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePlan(@LoginUser Long userId, @PathVariable Long planId) {
        planService.removePlan(planId, userId);
    }

    /** S5-1 계획 복제. 복제본은 부른 사람의 DRAFT 계획 */
    @PostMapping("/plans/{planId}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanCopyResponse copyPlan(@LoginUser Long userId, @PathVariable Long planId) {
        return planService.copyPlan(planId, userId);
    }
}
