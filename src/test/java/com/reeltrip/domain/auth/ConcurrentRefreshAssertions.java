package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.reeltrip.domain.auth.service.AuthService;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

final class ConcurrentRefreshAssertions {

    private ConcurrentRefreshAssertions() { }

    static void assertOnlyOneSuccess(AuthService service, String refreshToken) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청을 시작하지 못했어요.");
                }
                try {
                    return service.refresh(refreshToken).refreshToken();
                } catch (BusinessException error) {
                    if (error.getErrorCode() != ErrorCode.UNAUTHORIZED) {
                        throw error;
                    }
                    return "rejected";
                }
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter("rejected"::equals).count()).isEqualTo(1);
            assertThat(results.stream().filter(token -> !"rejected".equals(token)).toList())
                    .singleElement().matches(token -> !token.equals(refreshToken));
        } finally {
            start.countDown();
        }
    }
}
