package com.reeltrip.domain.auth.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetDispatcher.class);
    private final Executor executor;
    private final PasswordResetService service;

    public PasswordResetDispatcher(@Qualifier("passwordResetExecutor") Executor executor, PasswordResetService service) {
        this.executor = executor; this.service = service;
    }

    public void request(String email) {
        try {
            executor.execute(() -> {
                try { service.request(email); }
                catch (RuntimeException error) {
                    // 예외 본문·수신 주소·링크·비밀번호는 남기지 않아요.
                    log.warn("재설정 메일 작업 실패: {}", error.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException error) {
            log.warn("재설정 메일 작업 대기열이 가득 찼어요.");
        }
    }
}
