package com.reeltrip.domain.source.service;

import java.net.URI;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** 접수에 필요한 형식·플랫폼만 판별해요. DNS 조회나 외부 요청은 하지 않아요. */
@Component
public class SourceUrlParser {

    public ParsedUrl parse(String url) {
        try {
            if (url == null || url.isBlank() || url.length() > 2048) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            Platform platform = isHost(host, "youtube.com") || isHost(host, "youtu.be") ? Platform.YOUTUBE
                    : isHost(host, "instagram.com") ? Platform.INSTAGRAM
                    : isHost(host, "blog.naver.com") || host.equals("naver.me") ? Platform.NAVER_BLOG : Platform.ETC;
            return new ParsedUrl(url, platform);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isHost(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    public record ParsedUrl(String url, Platform platform) { }
}
