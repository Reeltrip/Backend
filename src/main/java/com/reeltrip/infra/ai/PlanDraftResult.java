package com.reeltrip.infra.ai;

import java.util.List;

/**
 * AI 초안 결과. days.get(i)는 (i+1)일차에 방문 순서대로 넣을 placeId 목록이에요(일자 수만큼, 빈 목록 가능).
 * 검사를 거친 값이라 후보에 없는 id, 중복, 숙소·교통·팁이 노드에 섞여 있지 않아요.
 */
public record PlanDraftResult(List<Long> stayPlaceIds, List<List<Long>> days) {
}
