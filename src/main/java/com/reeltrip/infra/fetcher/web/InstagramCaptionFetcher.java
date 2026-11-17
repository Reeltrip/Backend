package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.fetcher.ContentFetcher;
import com.reeltrip.infra.fetcher.FetchedContent;

/** 공개 HTML 메타데이터에 캡션이 있는 릴스·게시물(/p/)만 지원해요. 로그인·영상·사진 수집은 하지 않아요. */
@Component
public class InstagramCaptionFetcher implements ContentFetcher {
    // 인스타는 og:url을 /계정명/reel/코드/ 모양으로 내려줘서 앞의 계정명도 받아요.
    private static final Pattern PATH = Pattern.compile("(?:/[A-Za-z0-9._]{1,30})?/(reels?|p)/([A-Za-z0-9_-]{5,64})/?");
    private final SafeWebClient web;
    private final HtmlContentExtractor html;

    public InstagramCaptionFetcher(SafeWebClient web, HtmlContentExtractor html) {
        this.web = web;
        this.html = html;
    }

    @Override public Platform platform() { return Platform.INSTAGRAM; }

    @Override public FetchedContent fetch(String url) {
        try {
            URI requested = PublicNetworkPolicy.parse(url);
            String code = code(requested);
            var deadline = new FetchDeadline(Duration.ofSeconds(10));
            // 공유 값(?img_index=, ?igsh= 등)은 빼고 게시물 코드만으로 요청해요.
            String kind = isPost(requested) ? "p" : "reel";
            var page = web.fetch(URI.create("https://www.instagram.com/" + kind + "/" + code + "/"), deadline);
            if (!code(page.uri()).equals(code)) {
                throw new IOException("릴스·게시물 본문으로 이동하지 못했어요.");
            }
            var meta = html.extract(page, false);
            if (meta.canonical() == null || !code(PublicNetworkPolicy.parse(meta.canonical())).equals(code)) {
                throw new IOException("릴스·게시물 메타데이터가 없어요.");
            }
            String caption = meta.description();
            if (caption != null) {
                // 실제 설명문은 '좋아요 수 - 계정 on 날짜: "캡션". '처럼 따옴표 뒤에 마침표가 붙어요.
                String trimmed = caption.strip();
                if (trimmed.endsWith("\".")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                int quote = trimmed.indexOf(": \"");
                if (trimmed.endsWith("\"") && quote >= 0) {
                    caption = trimmed.substring(quote + 3, trimmed.length() - 1);
                }
            }
            if (caption == null || caption.isBlank() || caption.matches("(?is)^(log in to instagram|sign up.*instagram|create an account.*instagram|see instagram photos|instagram에 로그인|instagram에 가입|로그인하여|[0-9., kmb]+ likes).*")) {
                throw new IOException("공개 캡션이 없어요.");
            }
            caption = caption.strip();
            if (caption.length() > 2200) {
                int end = Character.isHighSurrogate(caption.charAt(2199)) ? 2199 : 2200;
                caption = caption.substring(0, end);
            }
            String thumbnail = meta.thumbnail() == null ? null : web.thumbnail(meta.thumbnail(), page.uri(), deadline);
            deadline.millis(10_000);
            return new FetchedContent(Platform.INSTAGRAM, url, caption, thumbnail);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.SOURCE_FETCH_FAILED);
        }
    }

    /** 릴스와 게시물은 코드만 비교해요. 영상 게시물은 /p/와 /reel/ 사이를 오가기도 해요. */
    private static String code(URI uri) throws IOException {
        return matcher(uri).group(2);
    }

    private static boolean isPost(URI uri) throws IOException {
        return matcher(uri).group(1).equals("p");
    }

    private static Matcher matcher(URI uri) throws IOException {
        if (uri.getHost() == null || !Set.of("instagram.com", "www.instagram.com", "m.instagram.com")
                .contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IOException("인스타 주소가 아니에요.");
        }
        var matcher = PATH.matcher(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (!matcher.matches()) {
            throw new IOException("릴스·게시물 하나의 주소가 필요해요.");
        }
        return matcher;
    }
}
