package com.reeltrip.domain.plan.dto;

import com.reeltrip.domain.plan.entity.ShareType;

/** POST /plans/{planId}/share-links 응답. shareUrl은 {서비스 주소}/plan/{planId} */
public record ShareLinkResponse(
        String shareUrl,
        ShareType shareType
) {
}
