package com.reeltrip.infra.ai;

import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.infra.fetcher.FetchedContent;

/**
 * 정해진 카드 2장(장소 1장 + 교통 1장)을 돌려주는 가짜 LlmClient. 카드는 docs/api-spec.md "LLM 출력 형식" 예시 그대로예요.
 * GEMINI_API_KEY가 비어 있을 때만 GeminiConfig가 이걸 빈으로 써요. 키 없이 링크 분석 흐름(S1-2 → S2-1)을 돌려 볼 때 써요.
 */
public class FakeLlmClient implements LlmClient {

    private static final String CITY = "후쿠오카";

    @Override
    public ExtractionResult extractCards(FetchedContent content) {
        // 영상 시점은 유튜브일 때만 의미가 있어요.
        Integer timestampSec = content.platform() == Platform.YOUTUBE ? 42 : null;
        PlaceDraft food = new PlaceDraft(
                null, Category.FOOD, "하카타 잇소", null, null, null, null, CITY, null, content.url(), timestampSec,
                List.of("돈코츠 라멘"), "¥960~980", null, null, "하카타역 동쪽 도보 약 6~7분", null,
                null, null, null, null, null, null, null);
        PlaceDraft transport = new PlaceDraft(
                null, Category.TRANSPORT, "지하철 1일권", null, null, null, null, CITY, null, content.url(), null,
                List.of(), null, null, null, null, null,
                "교통패스", "후쿠오카", "640엔", null, null, null, null);
        return new ExtractionResult(CITY, List.of(food, transport));
    }
}
