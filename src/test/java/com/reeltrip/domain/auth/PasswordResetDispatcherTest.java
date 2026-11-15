package com.reeltrip.domain.auth;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import com.reeltrip.domain.auth.service.*;

@ExtendWith(OutputCaptureExtension.class)
class PasswordResetDispatcherTest {
    @Test @DisplayName("계정 조회와 SMTP는 요청 스레드가 아니라 접수한 작업에서만 실행한다.")
    void queuesBeforeWork() {
        // given
        var work=new ArrayList<Runnable>(); var service=mock(PasswordResetService.class);
        var dispatcher=new PasswordResetDispatcher(work::add,service);
        // when
        dispatcher.request("private@example.test");
        // then
        verifyNoInteractions(service); work.getFirst().run();
        verify(service).request("private@example.test");
    }

    @Test @DisplayName("대기열 포화와 발송 실패는 호출자에게 계정 유무나 예외 속 토큰을 드러내지 않는다.")
    void hidesFailures(CapturedOutput output) {
        // given
        var service=mock(PasswordResetService.class);
        doThrow(new IllegalStateException("private-token")).when(service).request(anyString());
        // when
        new PasswordResetDispatcher(Runnable::run,service).request("private@example.test");
        new PasswordResetDispatcher(task->{throw new RejectedExecutionException("private-token");},service).request("private@example.test");
        // then
        assertThat(output.getAll()).doesNotContain("private-token","private@example.test");
    }
}
