package com.reeltrip.infra.fetcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Content-Length가 없거나 거짓이어도 실제 수신 바이트 수로 제한해요. */
final class LimitedBodySubscriber implements BodySubscriber<byte[]> {

    private final int limit;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedBodySubscriber(int limit) {
        this.limit = limit;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
            return;
        }
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> chunks) {
        if (body.isDone()) {
            return;
        }
        for (ByteBuffer chunk : chunks) {
            if (chunk.remaining() > limit - buffer.size()) {
                subscription.cancel();
                body.completeExceptionally(new IOException("메타데이터 응답 크기를 초과했어요."));
                return;
            }
            byte[] bytes = new byte[chunk.remaining()];
            chunk.get(bytes);
            buffer.writeBytes(bytes);
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
        body.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
        body.complete(buffer.toByteArray());
    }
}
