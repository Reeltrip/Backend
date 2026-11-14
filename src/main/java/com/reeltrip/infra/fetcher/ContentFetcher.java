package com.reeltrip.infra.fetcher;

import com.reeltrip.domain.source.entity.Platform;

/** 플랫폼별 수집기. #19·#20에서 본문 수집기를 같은 경계로 추가해요. */
public interface ContentFetcher {
    Platform platform();

    FetchedContent fetch(String url);
}
