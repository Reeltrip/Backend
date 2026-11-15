package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.fetcher.ContentFetcher;
import com.reeltrip.infra.fetcher.FetchedContent;

@Component
public class WebContentFetcher implements ContentFetcher {

    private final SafeWebClient client;
    private final HtmlContentExtractor extractor;

    public WebContentFetcher(SafeWebClient client, HtmlContentExtractor extractor) {
        this.client = client;
        this.extractor = extractor;
    }

    @Override public Platform platform() {
        return Platform.ETC;
    }

    @Override public FetchedContent fetch(String url) {
        var deadline = new FetchDeadline(Duration.ofSeconds(10));
        try {
            var page = client.fetch(PublicNetworkPolicy.parse(url), deadline);
            var content = extractor.extract(page, false);
            deadline.millis(10_000);
            if (content.text().isBlank()) {
                throw new IOException("본문 텍스트가 없어요.");
            }
            String thumbnail = content.thumbnail() == null ? null : client.thumbnail(content.thumbnail(), page.uri(), deadline);
            return new FetchedContent(Platform.ETC, url, content.text(), thumbnail);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.SOURCE_FETCH_FAILED);
        }
    }
}
