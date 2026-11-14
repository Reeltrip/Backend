package com.reeltrip.infra.http;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import com.reeltrip.infra.fetcher.LimitedBodySubscriber;
import jakarta.annotation.PreDestroy;

/** 호출자가 고정한 공식 API 주소 전용이에요. 사용자 웹 URL에는 SafeWebClient를 사용해요. */
@Component
public class BoundedHttp {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).proxy(ProxySelector.of(null)).build();

    public HttpResponse<byte[]> send(HttpRequest request, int maxBytes, Duration timeout) throws IOException {
        var future = client.sendAsync(request, info -> new LimitedBodySubscriber(maxBytes));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("외부 요청이 중단됐어요.");
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            future.cancel(true);
            // 예외 원문에 Maps 키나 인증 헤더가 섞이지 않게 해요.
            throw new IOException("외부 요청을 완료하지 못했어요.");
        }
    }

    @PreDestroy void shutdown() { client.shutdownNow(); }
}
