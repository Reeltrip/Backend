// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.infra.fetcher;

import com.reeltrip.domain.source.entity.Platform;

/**
 * 링크에서 가져온 내용. BE 1(infra/fetcher)이 만들어 BE 2의 LlmClient.extractCards에 넘겨요.
 * 정의는 docs/work-split.md 그대로예요.
 */
public record FetchedContent(
        Platform platform,   // YOUTUBE, INSTAGRAM, NAVER_BLOG, ETC
        String url,
        String text,         // 본문·캡션. 유튜브는 null (Gemini가 영상을 직접 읽음)
        String thumbnailUrl  // 장소 사진을 못 찾았을 때 대신 쓸 사진
) {
}
