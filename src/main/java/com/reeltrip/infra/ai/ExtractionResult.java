package com.reeltrip.infra.ai;

import java.util.List;

/** city는 LLM이 판단한 여행 도시(못 찾으면 null). cards의 각 PlaceDraft.city에도 같은 값을 넣어요. */
public record ExtractionResult(String city, List<PlaceDraft> cards) {
}
