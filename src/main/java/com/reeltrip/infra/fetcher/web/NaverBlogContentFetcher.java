package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.time.Duration;
import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.fetcher.ContentFetcher;
import com.reeltrip.infra.fetcher.FetchedContent;

@Component
public class NaverBlogContentFetcher implements ContentFetcher {

    private static final Pattern SHORT_PATH = Pattern.compile("/[A-Za-z0-9_-]{4,64}/?");

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
            URI original = PublicNetworkPolicy.parse(url);
            NaverBlogUrl article;
            SafeWebClient.Page page;
            if (isShort(original)) {
                // 추적 query를 전달하지 않고 HTTPS로 풀어요. 첫 블로그 글 이후에는 글이 바뀌지 않게 해요.
                URI shortUrl = URI.create("https://naver.me" + original.getRawPath().replaceAll("/+$", ""));
                NaverBlogUrl[] expected = {null};
                page = client.fetch(shortUrl, deadline, target -> {
                    if (isShort(target)) { return true; }
                    try {
                        var candidate = NaverBlogUrl.parse(target);
                        if (expected[0] != null && !expected[0].equals(candidate)) { return false; }
                        expected[0] = candidate;
                        return true;
                    } catch (IOException error) { return false; }
                });
                article = NaverBlogUrl.parse(page.uri());
            } else {
                article = NaverBlogUrl.parse(original);
                page = client.fetch(article.canonical(), deadline);
            }
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

    private static boolean isShort(URI uri) {
        return "naver.me".equalsIgnoreCase(uri.getHost()) && SHORT_PATH.matcher(uri.getRawPath()).matches();
    }
}
