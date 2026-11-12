package com.reeltrip.infra.ai;

/**
 * 보관함 카드로 계획 초안(숙소, 일자별 노드 순서)을 만드는 LLM 호출 (S4-2 AI 초안).
 * 실패하면 BusinessException(ErrorCode.AI_DRAFT_FAILED)를 던져요.
 */
public interface PlanDraftClient {

    PlanDraftResult draftPlan(PlanDraftRequest request);
}
