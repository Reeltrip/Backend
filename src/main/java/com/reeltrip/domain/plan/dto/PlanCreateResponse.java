package com.reeltrip.domain.plan.dto;

import com.reeltrip.domain.plan.entity.PlanStatus;

/** POST /plans 201. loadedPlaceCount는 계획 도시의 보관함 장소 카드 수 (S4-2 "보관함 노드 27개 불러옴") */
public record PlanCreateResponse(Long planId, PlanStatus status, int totalDayCount, long loadedPlaceCount) {
}
