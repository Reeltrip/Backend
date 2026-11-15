package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

record NaverBlogUrl(String blogId, String logNo) {

    static NaverBlogUrl parse(URI uri) throws IOException {
        if (!Set.of("blog.naver.com", "m.blog.naver.com").contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
            throw new IOException("네이버 블로그 본문 주소가 아니에요.");
        }
        String blogId = null;
        String logNo = null;
        String[] path = uri.getRawPath().split("/");
        if (path.length == 3) {
            blogId = path[1];
            logNo = path[2];
        } else if (uri.getRawPath().equals("/PostView.naver") && uri.getRawQuery() != null) {
            var values = new HashMap<String, String>();
            try {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String[] parts = parameter.split("=", 2);
                    String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    if (Set.of("blogId", "logNo").contains(key) && (parts.length != 2
                            || values.putIfAbsent(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null)) {
                        throw new IOException("본문 식별자가 중복되거나 비었어요.");
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("본문 식별자가 잘못됐어요.");
            }
            blogId = values.get("blogId");
            logNo = values.get("logNo");
        }
        if (blogId == null || !blogId.matches("[A-Za-z0-9_-]{1,64}") || logNo == null || !logNo.matches("[0-9]{1,20}")) {
            throw new IOException("블로그 글 하나의 주소가 필요해요.");
        }
        return new NaverBlogUrl(blogId, logNo);
    }

    URI canonical() {
        return URI.create("https://blog.naver.com/PostView.naver?blogId=" + blogId + "&logNo=" + logNo);
    }
}
