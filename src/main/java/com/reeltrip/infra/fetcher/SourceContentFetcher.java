package com.reeltrip.infra.fetcher;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;

@Component
public class SourceContentFetcher {

    private final SourceUrlClassifier urls;
    private final Map<Platform, ContentFetcher> fetchers = new EnumMap<>(Platform.class);

    public SourceContentFetcher(SourceUrlClassifier urls, List<ContentFetcher> fetchers) {
        this.urls = urls;
        for (var fetcher : fetchers) {
            if (this.fetchers.putIfAbsent(fetcher.platform(), fetcher) != null) {
                throw new IllegalArgumentException("한 플랫폼에는 수집기 하나만 등록할 수 있어요.");
            }
        }
    }

    public FetchedContent fetch(String url) {
        var fetcher = fetchers.get(urls.classify(url));
        if (fetcher == null) {
            throw SourceUrlClassifier.failed();
        }
        return fetcher.fetch(url);
    }
}
