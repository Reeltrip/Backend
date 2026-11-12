package com.reeltrip.domain.plan.dto;

import com.reeltrip.domain.plan.entity.ShareType;

import jakarta.validation.constraints.NotNull;

/** POST /plans/{planId}/share-links (S4-8). READ_ONLY·EDIT 밖의 값은 읽기 단계에서 INVALID_INPUT */
public record ShareLinkCreateRequest(
        @NotNull ShareType shareType
) {
}
