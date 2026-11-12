package com.reeltrip.domain.plan.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.domain.plan.service.PlanDraftService;
import com.reeltrip.global.security.LoginUser;

/** POST /plans/{planId}/ai-drafts (S4-2 AI 초안) */
@RestController
public class PlanDraftController {

    private final PlanDraftService planDraftService;

    public PlanDraftController(PlanDraftService planDraftService) {
        this.planDraftService = planDraftService;
    }

    /** 계획에 AI 초안을 채우고 계획 보기(GET /plans/{planId})와 같은 모양으로 돌려줘요. */
    @PostMapping("/plans/{planId}/ai-drafts")
    public PlanDetailResponse createAiDraft(@LoginUser Long userId, @PathVariable Long planId) {
        return planDraftService.createAiDraft(planId, userId);
    }
}
