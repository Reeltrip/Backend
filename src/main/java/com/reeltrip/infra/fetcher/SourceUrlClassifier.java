package com.reeltrip.infra.fetcher;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** 호스트를 조회하거나 원문 URL로 접속하지 않고 플랫폼만 판별해요. */
@Component
public class SourceUrlClassifier {

    private static final Set<String> YOUTUBE = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtu.be", "www.youtu.be");
    private static final Set<String> INSTAGRAM = Set.of("instagram.com", "www.instagram.com", "m.instagram.com");
    private static final Set<String> NAVER_BLOG = Set.of("blog.naver.com", "m.blog.naver.com");

    public Platform classify(String url) {
        String host = parse(url).getHost().toLowerCase(Locale.ROOT);
        if (YOUTUBE.contains(host)) {
            return Platform.YOUTUBE;
        }
        if (INSTAGRAM.contains(host)) {
            return Platform.INSTAGRAM;
        }
        return NAVER_BLOG.contains(host) ? Platform.NAVER_BLOG : Platform.ETC;
    }

    static URI parse(String url) {
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw failed();
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean https = "https".equalsIgnoreCase(scheme);
            boolean http = "http".equalsIgnoreCase(scheme);
            if ((!https && !http) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != (https ? 443 : 80))) {
                throw failed();
            }
            return uri;
        } catch (URISyntaxException e) {
            throw failed();
        }
    }

    static BusinessException failed() {
        return new BusinessException(ErrorCode.SOURCE_FETCH_FAILED);
    }
}
