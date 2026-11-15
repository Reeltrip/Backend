package com.reeltrip.infra.fetcher.web;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

final class FetchDeadline {
    private final long end;

    FetchDeadline(Duration duration) {
        end = System.nanoTime() + duration.toNanos();
    }

    int millis(int maximum) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("수집 작업이 중단됐어요.");
        }
        long remaining = (end - System.nanoTime()) / 1_000_000;
        if (remaining <= 0) {
            throw new SocketTimeoutException("본문 수집 시간이 지났어요.");
        }
        return (int) Math.min(remaining, maximum);
    }
}
