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
public class NaverBlogContentFetcher implements ContentFetcher {

    private final SafeWebClient client;
    private final HtmlContentExtractor extractor;

    public NaverBlogContentFetcher(SafeWebClient client, HtmlContentExtractor extractor) {
        this.client = client;
        this.extractor = extractor;
    }

    @Override public Platform platform() {
        return Platform.NAVER_BLOG;
    }

    @Override public FetchedContent fetch(String url) {
        var deadline = new FetchDeadline(Duration.ofSeconds(10));
        try {
            var article = NaverBlogUrl.parse(PublicNetworkPolicy.parse(url));
            var page = client.fetch(article.canonical(), deadline);
            var content = extractor.extract(page, true);
            if (content.text().isBlank() && content.mainFrame() != null) {
                var frame = PublicNetworkPolicy.parse(page.uri().resolve(content.mainFrame()).toString());
                if (!NaverBlogUrl.parse(frame).equals(article)) {
                    throw new IOException("다른 글의 프레임은 읽지 않아요.");
                }
                page = client.fetch(frame, deadline);
                content = extractor.extract(page, true);
            }
            deadline.millis(10_000);
            if (content.text().isBlank()) {
                throw new IOException("네이버 블로그 본문이 없어요.");
            }
            String thumbnail = content.thumbnail() == null ? null : client.thumbnail(content.thumbnail(), page.uri(), deadline);
            return new FetchedContent(Platform.NAVER_BLOG, url, content.text(), thumbnail);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.SOURCE_FETCH_FAILED);
        }
    }
}
