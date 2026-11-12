package com.reeltrip.domain.plan.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.plan.dto.ShareLinkCreateRequest;
import com.reeltrip.domain.plan.dto.ShareLinkResponse;
import com.reeltrip.domain.plan.entity.Plan;

/** 공유 링크 발급 (S4-8). 권한 확인은 PlanAccessService. 소유: plan-edit 팀원 */
@Service
public class PlanShareService {

    private final PlanAccessService planAccessService;
    private final String shareBaseUrl;

    public PlanShareService(PlanAccessService planAccessService,
                            @Value("${app.share.base-url}") String shareBaseUrl) {
        this.planAccessService = planAccessService;
        this.shareBaseUrl = shareBaseUrl.endsWith("/")
                ? shareBaseUrl.substring(0, shareBaseUrl.length() - 1)
                : shareBaseUrl;
    }

    /**
     * 공유를 켜거나 종류를 바꿔요(OWNER만). 공유 주소는 계획마다 하나라 종류만 바뀌어요.
     * 이미 멤버가 된 사람의 역할은 그대로 둬요.
     */
    @Transactional
    public ShareLinkResponse createShareLink(Long planId, Long userId, ShareLinkCreateRequest request) {
        Plan plan = planAccessService.getOwnedPlan(planId, userId).plan();
        plan.changeShareType(request.shareType());
        return new ShareLinkResponse(shareBaseUrl + "/plan/" + plan.getPlanId(), plan.getShareType());
    }
}
