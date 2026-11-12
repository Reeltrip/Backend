package com.reeltrip.infra.ai;

import com.reeltrip.infra.fetcher.FetchedContent;

/**
 * 링크 내용 → 카드 초안. BE 1의 AnalysisService가 부르고 BE 2가 Gemini로 구현해요 (docs/work-split.md).
 * 형식이 깨진 응답은 한 번만 다시 시도하고, 그래도 안 되거나 호출이 실패하면
 * BusinessException(ErrorCode.AI_EXTRACTION_FAILED)를 던져요.
 */
public interface LlmClient {

    ExtractionResult extractCards(FetchedContent content);
}
