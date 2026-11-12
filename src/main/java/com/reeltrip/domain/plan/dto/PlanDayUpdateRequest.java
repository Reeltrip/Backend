package com.reeltrip.domain.plan.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /plans/{planId}/days/{dayNumber} (S4-5). 그 날의 노드를 통째로 받아요.
 * 배열 순서가 orderIndex(0부터)예요.
 */
public record PlanDayUpdateRequest(
        @NotNull List<@NotNull @Valid Node> nodes,
        @NotNull Boolean isCompleted
) {

    public record Node(
            @NotNull Long placeId,
            List<@NotNull Long> alternativePlaceIds
    ) {

        /** 플랜B를 안 보내면 빈 목록으로 봐요 */
        public List<Long> alternativePlaceIdsOrEmpty() {
            return alternativePlaceIds == null ? List.of() : alternativePlaceIds;
        }
    }
}
