package com.reeltrip.domain.plan.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.plan.dto.ShareLinkCreateRequest;
import com.reeltrip.domain.plan.dto.ShareLinkResponse;
import com.reeltrip.domain.plan.service.PlanShareService;
import com.reeltrip.global.security.LoginUser;

import jakarta.validation.Valid;

/** POST /plans/{planId}/share-links. 소유: plan-edit 팀원 */
@RestController
public class PlanShareController {

    private final PlanShareService planShareService;

    public PlanShareController(PlanShareService planShareService) {
        this.planShareService = planShareService;
    }

    /** S4-8 공유 링크 발급 */
    @PostMapping("/plans/{planId}/share-links")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkResponse createShareLink(@LoginUser Long userId, @PathVariable Long planId,
                                             @Valid @RequestBody ShareLinkCreateRequest request) {
        return planShareService.createShareLink(planId, userId, request);
    }
}
