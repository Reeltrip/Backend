package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.fetcher.ContentFetcher;
import com.reeltrip.infra.fetcher.FetchedContent;

/** 공개 HTML 메타데이터에 캡션이 있는 릴스만 지원해요. 로그인·영상 수집은 하지 않아요. */
@Component
public class InstagramCaptionFetcher implements ContentFetcher {
    private static final Pattern PATH = Pattern.compile("/reel/([A-Za-z0-9_-]{5,64})/?");
    private final SafeWebClient web;
    private final HtmlContentExtractor html;

    public InstagramCaptionFetcher(SafeWebClient web, HtmlContentExtractor html) {
        this.web = web;
        this.html = html;
    }

    @Override public Platform platform() { return Platform.INSTAGRAM; }

    @Override public FetchedContent fetch(String url) {
        try {
            String code = code(PublicNetworkPolicy.parse(url));
            var deadline = new FetchDeadline(Duration.ofSeconds(10));
            var page = web.fetch(URI.create("https://www.instagram.com/reel/" + code + "/"), deadline);
            if (!code(page.uri()).equals(code)) {
                throw new IOException("릴스 본문으로 이동하지 못했어요.");
            }
            var meta = html.extract(page, false);
            if (meta.canonical() == null || !code(PublicNetworkPolicy.parse(meta.canonical())).equals(code)) {
                throw new IOException("릴스 메타데이터가 없어요.");
            }
            String caption = meta.description();
            if (caption != null && caption.endsWith("\"")) {
                int quote = caption.indexOf(": \"");
                if (quote >= 0) {
                    caption = caption.substring(quote + 3, caption.length() - 1);
                }
            }
            if (caption == null || caption.isBlank() || caption.matches("(?is)^(log in to instagram|sign up.*instagram|see instagram photos|instagram에 로그인|로그인하여|[0-9., kmb]+ likes).*")) {
                throw new IOException("공개 캡션이 없어요.");
            }
            caption = caption.strip();
            if (caption.length() > 2200) {
                int end = Character.isHighSurrogate(caption.charAt(2199)) ? 2199 : 2200;
                caption = caption.substring(0, end);
            }
            String thumbnail = meta.thumbnail() == null ? null : web.thumbnail(meta.thumbnail(), page.uri(), deadline);
            return new FetchedContent(Platform.INSTAGRAM, url, caption, thumbnail);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.SOURCE_FETCH_FAILED);
        }
    }

    private static String code(URI uri) throws IOException {
        if (uri.getHost() == null || !Set.of("instagram.com", "www.instagram.com", "m.instagram.com")
                .contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IOException("릴스 주소가 아니에요.");
        }
        var matcher = PATH.matcher(uri.getRawPath());
        if (!matcher.matches()) {
            throw new IOException("릴스 하나의 주소가 필요해요.");
        }
        return matcher.group(1);
    }
}
