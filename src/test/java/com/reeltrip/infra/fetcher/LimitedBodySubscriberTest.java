package com.reeltrip.infra.fetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitedBodySubscriberTest {

    @Test
    @DisplayName("여러 청크의 실제 바이트를 합쳐 제한 크기까지 읽고 완료한다.")
    void acceptsChunkedBodyAtLimit() {
        // given
        var subscriber = new LimitedBodySubscriber(5);
        var subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        // when
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{4, 5})));
        subscriber.onComplete();
        // then
        assertThat(subscriber.getBody().toCompletableFuture().join()).containsExactly(1, 2, 3, 4, 5);
        verify(subscription, never()).cancel();
    }

    @Test
    @DisplayName("청크 응답이 제한을 넘으면 즉시 구독을 취소하고 더는 본문을 저장하지 않는다.")
    void cancelsOversizedBody() {
        // given
        var subscriber = new LimitedBodySubscriber(5);
        var subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[4])));
        // when
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[2])));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[100])));
        subscriber.onComplete();
        // then
        verify(subscription).cancel();
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join()).hasCauseInstanceOf(IOException.class);
    }
}
