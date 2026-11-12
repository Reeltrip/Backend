package com.reeltrip.domain.plan.service;

import org.springframework.stereotype.Service;

import com.reeltrip.domain.plan.dto.ShareLinkCreateRequest;
import com.reeltrip.domain.plan.dto.ShareLinkResponse;

/** 공유 링크 발급 (S4-8). 권한 확인은 PlanAccessService. 소유: plan-edit 팀원 */
@Service
public class PlanShareService {

    public ShareLinkResponse createShareLink(Long planId, Long userId, ShareLinkCreateRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return new ShareLinkResponse("http://localhost:5173/plan/" + planId, request.shareType());
    }
}
