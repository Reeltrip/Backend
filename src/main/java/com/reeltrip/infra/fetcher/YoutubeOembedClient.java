package com.reeltrip.infra.fetcher;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

/** 원문·썸네일 주소에는 접속하지 않고 고정된 YouTube oEmbed 주소만 호출해요. */
@Component
public class YoutubeOembedClient {

    static final int MAX_BYTES = 64 * 1024;
    private static final Set<Integer> UNAVAILABLE = Set.of(400, 401, 403, 404, 410);
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Duration totalTimeout;

    @Autowired
    public YoutubeOembedClient(ObjectMapper mapper) {
        this(mapper, newClient(), Duration.ofSeconds(5));
    }

    YoutubeOembedClient(ObjectMapper mapper, HttpClient client, Duration totalTimeout) {
        this.mapper = mapper;
        this.client = client;
        this.totalTimeout = totalTimeout;
    }

    static HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).proxy(ProxySelector.of(null)).build();
    }

    @PreDestroy
    void shutdown() {
        client.shutdownNow();
    }

    public Optional<Metadata> fetch(String videoId) {
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            throw SourceUrlClassifier.failed();
        }
        // 원문 query·host를 넘기지 않고 검증한 영상 ID만으로 요청 주소를 만들어요.
        String video = "https://www.youtube.com/watch?v=" + videoId;
        URI endpoint = URI.create("https://www.youtube.com/oembed?format=json&url="
                + URLEncoder.encode(video, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json").header("Accept-Encoding", "identity").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> {
            if (UNAVAILABLE.contains(info.statusCode())) {
                throw new UnavailableVideoException();
            }
            return new LimitedBodySubscriber(MAX_BYTES);
        });
        try {
            var response = pending.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (UNAVAILABLE.contains(response.statusCode())) {
                throw SourceUrlClassifier.failed();
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip();
            String encoding = response.headers().firstValue("Content-Encoding").orElse("identity");
            if (response.statusCode() != 200 || !contentType.equalsIgnoreCase("application/json")
                    || !encoding.equalsIgnoreCase("identity")) {
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.isObject() || !root.path("type").asText().equals("video")) {
                return Optional.empty();
            }
            return Optional.of(new Metadata(title(root.path("title")), thumbnail(root.path("thumbnail_url"), videoId)));
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw SourceUrlClassifier.failed();
        } catch (TimeoutException e) {
            pending.cancel(true);
            return Optional.empty();
        } catch (CancellationException e) {
            throw SourceUrlClassifier.failed();
        } catch (ExecutionException e) {
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof UnavailableVideoException) {
                    throw SourceUrlClassifier.failed();
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String title(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 300 ? value.asText() : null;
    }

    private static String thumbnail(JsonNode value, String videoId) {
        if (!value.isTextual()) {
            return null;
        }
        try {
            URI uri = SourceUrlClassifier.parse(value.asText());
            if ("https".equalsIgnoreCase(uri.getScheme()) && "i.ytimg.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getRawPath().matches("/(vi|vi_webp)/" + Pattern.quote(videoId) + "/[A-Za-z0-9_-]+\\.(jpg|webp)")) {
                return value.asText();
            }
        } catch (RuntimeException e) {
            // 잘못된 썸네일 주소만 버리고 영상 분석은 계속해요.
        }
        return null;
    }

    public record Metadata(String title, String thumbnailUrl) {
    }

    private static final class UnavailableVideoException extends RuntimeException {
    }
}
