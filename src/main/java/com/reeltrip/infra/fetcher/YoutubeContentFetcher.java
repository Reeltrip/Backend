package com.reeltrip.infra.fetcher;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.source.entity.Platform;

@Component
public class YoutubeContentFetcher implements ContentFetcher {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private final SourceUrlClassifier urls;
    private final YoutubeOembedClient metadata;

    public YoutubeContentFetcher(SourceUrlClassifier urls, YoutubeOembedClient metadata) {
        this.urls = urls;
        this.metadata = metadata;
    }

    @Override
    public Platform platform() {
        return Platform.YOUTUBE;
    }

    @Override
    public FetchedContent fetch(String url) {
        if (urls.classify(url) != Platform.YOUTUBE) {
            throw SourceUrlClassifier.failed();
        }
        String id = videoId(SourceUrlClassifier.parse(url));
        String thumbnail = metadata.fetch(id).map(YoutubeOembedClient.Metadata::thumbnailUrl).orElse(null);
        // 자막·제목을 본문으로 바꾸지 않아요. 기존 Gemini가 영상 자체를 읽어요.
        return new FetchedContent(Platform.YOUTUBE, url, null, thumbnail);
    }

    private static String videoId(URI uri) {
        String path = uri.getRawPath();
        String id = null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ((host.equals("youtu.be") || host.equals("www.youtu.be")) && path.length() > 1) {
            id = path.substring(1).replaceFirst("/$", "");
        } else if (path.startsWith("/shorts/")) {
            id = path.substring("/shorts/".length()).replaceFirst("/$", "");
        } else if (path.equals("/watch") && uri.getRawQuery() != null) {
            try {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String[] parts = parameter.split("=", 2);
                    if (URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals("v")) {
                        if (id != null || parts.length != 2) {
                            throw SourceUrlClassifier.failed();
                        }
                        id = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    }
                }
            } catch (IllegalArgumentException e) {
                throw SourceUrlClassifier.failed();
            }
        }
        if (id == null || !VIDEO_ID.matcher(id).matches()) {
            throw SourceUrlClassifier.failed();
        }
        return id;
    }
}
